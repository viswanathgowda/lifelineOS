package lifelineOS.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileApiIntegrationTest {

	private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicStatusDoesNotRequireAuth() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Lifeline OS Running"));

		mockMvc.perform(get("/api/tunnel/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gateway").value("lifeline-tunnel"));
	}

	@Test
	void filesRequireAuth() throws Exception {
		mockMvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
	}

	@Test
	void uploadListReadDeleteRoundTrip() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"note.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"hello vault".getBytes());

		MvcResult upload = mockMvc.perform(multipart("/api/files")
						.file(file)
						.param("namespace", "DOCUMENTS")
						.header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("note.txt"))
				.andExpect(jsonPath("$.namespace").value("DOCUMENTS"))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andReturn();

		String id = extractId(upload.getResponse().getContentAsString());

		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id));

		MvcResult content = mockMvc.perform(get("/api/files/{id}/content", id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(content.getResponse().getContentAsByteArray()).isEqualTo("hello vault".getBytes());

		mockMvc.perform(delete("/api/files/{id}", id)
						.header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(status().isNoContent());
	}

	@Test
	void healthDomainRequiresToken() throws Exception {
		mockMvc.perform(post("/api/health")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"metric\":\"heart_rate\",\"value\":72,\"unit\":\"bpm\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/health")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"metric\":\"heart_rate\",\"value\":72,\"unit\":\"bpm\"}")
						.header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.metric").value("heart_rate"));
	}

	private static String extractId(String json) {
		Matcher matcher = ID_PATTERN.matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
