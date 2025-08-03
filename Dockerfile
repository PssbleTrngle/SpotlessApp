FROM gradle:latest AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle buildFatJar --no-daemon

FROM amazoncorretto:22 as runtime

RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/server.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/server.jar"]