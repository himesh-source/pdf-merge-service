# pdf-merge-service
Merge pdf documents

Run your Spring Boot WebFlux app:
./gradlew bootRun
Open Swagger JSON directly:
http://localhost:8080/v3/api-docs
Or open Swagger UI:
http://localhost:8080/swagger-ui.html
You’ll see:
/api/pdf/mergeAndSaveToDisk
Enter the source dir and output file directory
Merged file will be saved to Path
/api/merge listed.
Choose Files button for multiple uploads.
Response content types: PDF or ZIP.
