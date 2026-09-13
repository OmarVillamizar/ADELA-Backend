### STAGE 1:BUILD ###
FROM maven:3-amazoncorretto-17-alpine AS build

# Create app directory
WORKDIR /app

# Copy the pom.xml file
COPY pom.xml .

# Download the dependencies
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src ./src

# Build the application
# Los tests corren en la imagen: son unitarios, sin base de datos ni red, asi que
# un fallo de los guardas de acceso detiene el build en lugar de publicarse.
RUN mvn clean package


### STAGE 2:DEPLOY ###
FROM amazoncorretto:17-alpine AS deploy

# Create app directory
WORKDIR /app

# Copy the built jar file
COPY --from=build /app/target/*.jar app.jar

# Expose the port
EXPOSE 8091

# Create a volume for the uploads
VOLUME /app/uploads

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]