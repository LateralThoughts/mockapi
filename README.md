# Mockapi

## Assemble the jar
    mvn assembly:assembly

## Run the jar :
    java -jar target/mockapi-1.0.0-SNAPSHOT-jar-with-dependencies.jar

## Usage

    POST /{anything_you_want} (with JSon object on body)

    GET /{anything_you_want}

    GET /anything_you_want}?{attr_in_json_body}={exactMatch}

    GET /anything_you_want}?unwrap={attr_in_json_body_whose_reference_other_resource}

## Extra infos

This project was created during the Lateral Thougths devweek in Java and Python.
Here is the python code : https://github.com/MaximeGaudin/APIMorph
