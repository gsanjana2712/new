FROM tomcat:9.0.73-jdk11-temurin-jammy
COPY target/testapp.war /usr/local/tomcat/webapps/testapp.war
CMD ["catalina.sh", "run"]