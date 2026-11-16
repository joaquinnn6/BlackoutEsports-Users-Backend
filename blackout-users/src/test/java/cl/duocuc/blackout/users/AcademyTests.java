package cl.duocuc.blackout.users;

import cl.duocuc.blackout.users.academy.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="academy.cognito-userinfo-url=https://academy.test/oauth2/userInfo")
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@ActiveProfiles("test")
@Import(TestTokens.class)
class AcademyTests {
    @Autowired MockMvc mvc;
    @Autowired MockRestServiceServer cognito;
    @Autowired AcademyRepository applications;
    @Autowired ObjectMapper mapper;
    final String mine = "/api/academy/me";
    final String staff = "/api/academy/applications";
    final String form = """
        {"alias":"Camila","game":"Valorant","role":"Duelista","rankName":"Oro",
         "availability":"Sábado","motivation":"Jugar en equipo"}
        """;

    @BeforeEach void clear() { applications.deleteAll(); }

    @Test void correoVerificadoRevisionUnicaYEventoPendiente() throws Exception {
        String fan = "Bearer " + TestTokens.fan();
        String admin = "Bearer " + TestTokens.staff();
        cognito.expect(requestTo("https://academy.test/oauth2/userInfo")).andRespond(withSuccess(
            "{\"sub\":\"fan-1\",\"email\":\"fan@example.org\",\"email_verified\":\"true\"}", MediaType.APPLICATION_JSON));
        var response = mvc.perform(post(mine).header("Authorization",fan).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.decision").value("PENDING")).andReturn();
        String id = mapper.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get(staff).header("Authorization",fan)).andExpect(status().isForbidden());
        mvc.perform(get(mine).header("Authorization",fan)).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post(mine).header("Authorization",fan).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isConflict());
        String path = staff + "/" + id + "/decision";
        mvc.perform(put(path).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.delivery").value("PENDING"));
        assertThat(applications.findById(UUID.fromString(id)).orElseThrow().pendingPublication).isTrue();
        mvc.perform(put(path).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isConflict());
    }

    @Test void correoSinVerificarSeRechaza() throws Exception {
        cognito.expect(requestTo("https://academy.test/oauth2/userInfo")).andRespond(withSuccess(
            "{\"sub\":\"fan-1\",\"email\":\"fan@example.org\",\"email_verified\":false}", MediaType.APPLICATION_JSON));
        mvc.perform(post(mine).header("Authorization","Bearer " + TestTokens.fan()).contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isForbidden());
        assertThat(applications.count()).isZero();
    }
}
