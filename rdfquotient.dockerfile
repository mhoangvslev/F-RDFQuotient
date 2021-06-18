FROM maven:3.8.1-jdk-8-openj9


RUN apt-get update && \
    apt-get install -y -qq \
      git graphviz nano curl \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /code
RUN git clone https://gitlab.inria.fr/cedar/RDFQuotient.git
RUN curl http://files.inria.fr/cedar/RDFQuotient/ontosql-rdfdb-1.0.10-SNAPSHOT-with-dependencies.jar \
        --output ontosql.jar  \
    && mvn -q install:install-file -Dfile=/code/ontosql.jar \
         -DgroupId=fr.inria.cedar.ontosql \
         -DartifactId=ontosql-rdfdb \
         -Dversion=1.0.10-SNAPSHOT \
         -Dpackaging=jar

WORKDIR /code/RDFQuotient

RUN mvn clean install -DskipTests -q -B -ntp

ENTRYPOINT [ "java", "-jar", "./target/RDFQuotient-1.8-with-dependencies.jar" ]
CMD [ "--help" ]
