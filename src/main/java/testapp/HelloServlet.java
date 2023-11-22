package testapp;

//import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

//@WebServlet("/hello")
public class HelloServlet extends HttpServlet {
    /**
	 * Hello World
	 */
	private static final long serialVersionUID = 1L;
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.getWriter().write("hello! I am in dev environment.");
    }
}
