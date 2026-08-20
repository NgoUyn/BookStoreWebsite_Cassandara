#1. Mượn một hệ điều hành Linux mini có cài sẵn java 17
FROM eclipse-temurin:17-jdk-alpine

#2. Tạo một thư mục tên là /app bên trong cái máy ảo Docker đó
WORKDIR /app

# 3. Copy cái file .jar (sản phẩm sau khi Maven build xong) từ máy bên ngoài vào trong thư mục /app
COPY target/*.jar app.jar

# 4. Lệnh để chạy project Spring Boot khi khởi động Container
ENTRYPOINT ["java", "-jar", "app.jar"]