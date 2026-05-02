AI-powered music streaming platform built with Spring Boot and Angular, supporting voice search, melody recognition, and personalized recommendation.

📄 [Academic Project Report – Intelligent Music Streaming Platform](https://drive.google.com/file/d/1P1QS8XyIziccBlSkSWgkqkkqTcCKosu0/view)

## ⚙️ Configuration (`application.properties`)

```properties
# Application Context Path
server.servlet.context-path=/symphony

# Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/symphony_sa
spring.datasource.username=your_mysql_username
spring.datasource.password=your_mysql_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# File Upload Configuration
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# JWT Configuration
jwt.secretKey=your_jwt_secret_key
jwt.validDuration=604800
jwt.refreshableDuration=2592000

# AI Service URL (Python Backend)
ai.service.url=http://localhost:8000
```
