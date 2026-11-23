# Blackout Academy · entrega 3

## Flujo y responsabilidades

El frontend abre la vista con `#academy`. Una cuenta Cognito presenta el formulario. Una cuenta Entra con el rol Admin o Staff entra desde Postulaciones Academy en el panel, revisa los antecedentes y confirma la decisión. La fecha del navegador se convierte a un instante UTC. La citación muestra la hora de Santiago tanto en la web como en el correo.

El módulo Java está en `blackout-users/src/main/java/cl/duocuc/blackout/users/academy`. Comparte el servicio Users, su ruta pública a través de API Gateway y Nginx, PostgreSQL y la conexión AMQP. El proceso consumidor es un listener dentro de ese servicio. La configuración de la entrega 2 sigue atendiendo eventos de torneos.

| Método | Ruta dentro de Users | Identidad y resultado |
|---|---|---|
| POST | /api/academy/me | Fan. Guarda una postulación pendiente. |
| GET | /api/academy/me | Fan. Retorna su postulación o 204. |
| GET | /api/academy/applications | Admin o Staff. Lista ordenada por fecha. |
| PUT | /api/academy/applications/{id}/decision | Admin o Staff. Invita al tryout o rechaza una solicitud pendiente. |

SecurityConfig valida la autorización en el servidor. AcademyController obtiene el correo verificado consultando Cognito UserInfo con el access token y comparando el identificador sub del resultado con el del token validado. El formulario no decide el destinatario. Se admite una postulación por cuenta en esta convocatoria. Un índice único impide duplicados y un bloqueo por fila impide dos decisiones simultáneas. La tabla academy_applications registra la decisión, fecha, identidad del evaluador y resultado del envío.

## Mensajería

| Exchange | Tipo | Binding | Destino |
|---|---|---|---|
| blackout.events.exchange | topic | academy.application.accepted | academy.email.queue |
| blackout.events.exchange | topic | academy.application.rejected | academy.email.queue |
| blackout.dead-letter.exchange | direct | academy.email.failed | academy.email.dlq |
| blackout.events.exchange | topic | academy.application.* | academy.audit.queue |
| blackout.dead-letter.exchange | direct | academy.audit.failed | academy.audit.dlq |

AcademyMessaging declara las colas de correo y auditoría, sus DLQ y sus bindings. La routing key viaja con el evento. El binding es la regla con la que el exchange elige la cola. El exchange topic acepta patrones y aquí las dos coincidencias son exactas. El direct de fallos compara la clave exacta. El cuerpo del evento sólo contiene applicationId. El consumidor recupera desde PostgreSQL el destinatario y la decisión definitiva.

1. Al decidir se guardan la decisión y pendingPublication en una transacción. Esta marca implementa una outbox en la misma fila, válida porque hay una única decisión definitiva por solicitud.
2. AcademyPublisher consulta una fila pendiente cada dos segundos, publica con RabbitTemplate y espera confirmación del broker. La publicación obligatoria permite detectar un evento devuelto por falta de ruta. Si falla, la transacción conserva la marca y se vuelve a intentar. Un broker activo y la base de datos son necesarios para avanzar.
3. AcademyMailService bloquea la fila, prepara la plantilla, llama al servidor SMTP mediante STARTTLS y registra SENT cuando el proveedor acepta el correo. El contenedor usa ACK automático después de que retorna el listener transaccional. SENT indica aceptación SMTP. La entrega al buzón y la lectura son etapas posteriores.
4. Ante errores del listener se hacen tres intentos totales, con pausas de uno y dos segundos. Si se agotan, el recuperador intenta registrar FAILED y rechaza sin reencolar. RabbitMQ usa el exchange y la clave de fallos definidos en academy.email.queue. Una redelivery después de SENT se omite. Si el proceso cae justo después de que SMTP acepta el correo y antes de guardar SENT, un duplicado sigue siendo posible.
5. La pantalla permite actualizar el estado del correo. Para investigar una DLQ se revisan applicationId, las cabeceras de RabbitMQ y los logs del servicio. Corregida la causa, un operador puede republicar el evento original con su routing key de decisión. Conservar el original hasta verificar el resultado.

## Configuración del despliegue

Usar la rama feature-entrega-final en Users y en el frontend. En EC2, en el archivo .env del proyecto Compose de Users, configurar ACADEMY_COGNITO_USERINFO_URL con el dominio HTTPS del Hosted UI de su pool seguido de /oauth2/userInfo. El cliente Cognito solicita openid y email. Si crean una rama nueva en Amplify, registrar sus URLs de retorno en Cognito y Entra, su origen en CORS y las variables Vite de esa rama.

Configurar ACADEMY_MAIL_FROM, SPRING_MAIL_HOST, SPRING_MAIL_PORT (587), SPRING_MAIL_USERNAME y SPRING_MAIL_PASSWORD con el proveedor SMTP elegido. Guardar las credenciales en el entorno de despliegue. Si el proveedor exige verificar el remitente o los destinatarios de prueba, completar esa verificación. Activar ACADEMY_MAIL_CONSUMER_ENABLED=true y recrear el servicio api de Users. Con el consumidor desactivado, el broker conserva los eventos listos para enviar. Las notificaciones de torneos usan otra cola.

## Demostración

Con dos sesiones, enviar una postulación de fan y revisarla con Staff. Con el consumidor de Academy detenido, invitar al tryout y observar una nueva entrega Ready en academy.email.queue. Activar el consumidor y verificar el correo real y el estado del envío. Repetir con un rechazo. Para probar la DLQ, usar un entorno de prueba con un SMTP que rechace los envíos y comprobar el estado FAILED y la entrada en academy.email.dlq. El servicio de torneos puede seguir activo durante la prueba.

Las pruebas automatizadas usan H2 y dobles de Cognito y SMTP. Ejecutar mvn test en blackout-users. La prueba final en la nube requiere cuentas de ambos proveedores, un RabbitMQ activo y un buzón de prueba.

## Auditoría independiente del correo

La misma decisión se enruta a academy.email.queue y academy.audit.queue. Cada cola tiene su propio consumidor: no compiten entre ellas. AcademyAuditService registra applicationId, decisión, identificador del evaluador, fecha de decisión y fecha de registro en academy_decision_audit. Usa un bloqueo por postulación y clave única para no duplicar una decisión. Un fallo de SMTP no impide que el consumidor de auditoría avance. Después de tres intentos fallidos, el consumidor de auditoría rechaza sin reencolar y el evento llega a academy.audit.dlq. El registro se consulta mediante GET /api/academy/applications/audit, solo con Admin/Staff, desde “Ver registro de decisiones”. No requiere introducir el nombre del evaluador: procede del JWT de la decisión original.

Conservamos la cola fan.notifications.queue de novedades de torneos, con fan.notifications.dlq. Así existen tres colas funcionales y tres DLQ. El reintento de las notificaciones espera publisher confirm antes de hacer ACK del original; si la republicación falla, hace NACK con requeue=true. Los fallos agotados registran el error antes del rechazo.

## Prueba reproducible de RabbitMQ y SMTP

La tarea rabbit-integration del workflow levanta dos nodos desde la rama feature-entrega-final de RabbitAdmin, comprueba que forman un clúster y levanta Mailpit. RabbitClusterTests prueba correo de aceptación y rechazo, entrega independiente a auditoría, notificaciones de torneos y las tres DLQ. Para el fallo SMTP usa un error controlado y comprueba tres llamadas al remitente. Los mensajes válidos se entregan al SMTP real de Mailpit y se verifica el texto recibido mediante su API. Mailpit es un buzón de pruebas, no envía correos a cuentas externas. Los destinatarios de CI son ficticios de example.org. Las pruebas normales siguen usando dobles y H2; las pruebas de integración usan RabbitMQ y SMTP reales, con H2 para Users.

Para producción se exige STARTTLS y credenciales del proveedor SMTP. Solo RabbitClusterTests desactiva TLS/autenticación para conectarse al contenedor de prueba aislado. No trasladar esos overrides a producción. SENT significa aceptación por SMTP; no garantiza llegada al buzón ni lectura. La idempotencia evita reenvíos ya registrados, pero puede haber duplicados si el proceso cae después de enviar y antes de confirmar la transacción.

Para dos nodos configurar RABBITMQ_ADDRESSES=rabbit1:5672,rabbit2:5672. Las rutas de novedades se separan para el Gateway: Cognito usa GET /api/notifications y Entra usa GET /api/notifications/staff. Ambas devuelven las mismas novedades; el backend restringe la segunda a Admin/Staff.
