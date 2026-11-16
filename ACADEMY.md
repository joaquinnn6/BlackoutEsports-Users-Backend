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

AcademyMessaging declara las colas durables y los tres bindings. La routing key viaja con el evento. El binding es la regla con la que el exchange elige la cola. El exchange topic acepta patrones y aquí las dos coincidencias son exactas. El direct de fallos compara la clave exacta. El cuerpo del evento sólo contiene applicationId. El consumidor recupera desde PostgreSQL el destinatario y la decisión definitiva.

1. Al decidir se guardan la decisión y pendingPublication en una transacción. Esta marca implementa una outbox en la misma fila, válida porque hay una única decisión definitiva por solicitud.
2. AcademyPublisher consulta una fila pendiente cada dos segundos, publica con RabbitTemplate y espera confirmación del broker. La publicación obligatoria permite detectar un evento devuelto por falta de ruta. Si falla, la transacción conserva la marca y se vuelve a intentar. Un broker activo y la base de datos son necesarios para avanzar.
3. AcademyMailService bloquea la fila, prepara la plantilla, llama al servidor SMTP mediante STARTTLS y registra SENT cuando el proveedor acepta el correo. El contenedor usa ACK automático después de que retorna el listener transaccional. SENT indica aceptación SMTP. La entrega al buzón y la lectura son etapas posteriores.
4. Ante errores del listener se hacen tres intentos totales, con pausas de uno y dos segundos. Si se agotan, el recuperador intenta registrar FAILED y rechaza sin reencolar. RabbitMQ usa el exchange y la clave de fallos definidos en academy.email.queue. Una redelivery después de SENT se omite. Si el proceso cae justo después de que SMTP acepta el correo y antes de guardar SENT, un duplicado sigue siendo posible.
5. La pantalla permite actualizar el estado del correo. Para investigar una DLQ se revisan applicationId, las cabeceras de RabbitMQ y los logs del servicio. Corregida la causa, un operador puede republicar el evento original con su routing key de decisión. Conservar el original hasta verificar el resultado.

## Configuración del despliegue

Usar la rama feature-entrega-3-academy en Users y en el frontend. En EC2, en el archivo .env del proyecto Compose de Users, configurar ACADEMY_COGNITO_USERINFO_URL con el dominio HTTPS del Hosted UI de su pool seguido de /oauth2/userInfo. El cliente Cognito solicita openid y email. Si crean una rama nueva en Amplify, registrar sus URLs de retorno en Cognito y Entra, su origen en CORS y las variables Vite de esa rama.

Configurar ACADEMY_MAIL_FROM, SPRING_MAIL_HOST, SPRING_MAIL_PORT (587), SPRING_MAIL_USERNAME y SPRING_MAIL_PASSWORD con el proveedor SMTP elegido. Guardar las credenciales en el entorno de despliegue. Si el proveedor exige verificar el remitente o los destinatarios de prueba, completar esa verificación. Activar ACADEMY_MAIL_CONSUMER_ENABLED=true y recrear el servicio api de Users. Con el consumidor desactivado, el broker conserva los eventos listos para enviar. Las notificaciones de torneos usan otra cola.

## Demostración

Con dos sesiones, enviar una postulación de fan y revisarla con Staff. Con el consumidor de Academy detenido, invitar al tryout y observar una nueva entrega Ready en academy.email.queue. Activar el consumidor y verificar el correo real y el estado del envío. Repetir con un rechazo. Para probar la DLQ, usar un entorno de prueba con un SMTP que rechace los envíos y comprobar el estado FAILED y la entrada en academy.email.dlq. El servicio de torneos puede seguir activo durante la prueba.

Las pruebas automatizadas usan H2 y dobles de Cognito y SMTP. Ejecutar mvn test en blackout-users. La prueba final en la nube requiere cuentas de ambos proveedores, un RabbitMQ activo y un buzón de prueba.
