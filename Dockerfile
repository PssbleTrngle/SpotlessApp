FROM gradle:latest AS build

COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN gradle buildFatJar --no-daemon

FROM amazoncorretto:22-alpine as runtime

RUN apk add --no-cache git

WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar server.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","server.jar"]