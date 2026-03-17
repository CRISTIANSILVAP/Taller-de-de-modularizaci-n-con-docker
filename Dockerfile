FROM eclipse-temurin:21-jdk
 
WORKDIR /usrapp/bin
 
ENV PORT 6000

EXPOSE 6000
 
COPY /target/classes /usrapp/bin/classes


CMD ["java","-cp","./classes","co.edu.escuelaing.MicroSpringBoot"]
 