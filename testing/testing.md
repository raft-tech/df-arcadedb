# Testing


In Keycloak, add the following attributes to the admin user:
```
clearance-usa = U
nationality = USA
```

### Confirm that the user can create a Secret database
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/server "Authorization: Bearer ${TOKEN}" \
  < create_secret_database.json
```

### Create a new vertex
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
  < create_people_vertex.json
```

### Confirm that the user cannot add an object with missing overall classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
  < insert_person_missing_classification.json
```

### Confirm that the user cannot add a Top Secret object to the Secret database
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_ts_person.json
```
### Confirm that the user cannot add a object they would not have access to
### Confirm that the user can add an object with correct general classification
### Confirm that the user can add an object with correct source classification
### Attempt to add a record with a higher classification
### Create a backup of the database
# OTHER
### Add a third unclass record
### rollback to previous state
###
Create a new DB with a USA-U user.
 
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && curl post :/api/v1/arcadedb/server "Authorization: Bearer ${TOKEN}" \
  "command=list databases" 
```


```shell
http post :8080/adtech/polygon-response \
  < search_record_response.json
```