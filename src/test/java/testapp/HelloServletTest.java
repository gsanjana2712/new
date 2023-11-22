package testapp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HelloServletTest {
    @Test
    public void testHelloServlet() throws Exception {
        HelloServlet servlet = new HelloServlet();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doGet(request, response);

        assertEquals("hello! I am in dev environment.", response.getContentAsString());
    }
}
