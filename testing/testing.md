# Testing


In Keycloak, add the following attributes to the admin user:
```
clearance-usa = U
nationality = USA
```

1. Confirm that the user can create a Secret database
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/server "Authorization: Bearer ${TOKEN}" \
  < create_secret_database.json
```

2. Create a new vertex
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
  < create_people_vertex.json
```

3. Confirm that the user cannot add an object with missing overall classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
  && http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
  < insert_person_missing_classification.json
```

4. Confirm that the user cannot add a Top Secret object to the Secret database
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_ts_person.json
```

5. Confirm that the user cannot add an object they would not have access to
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_person.json
```

6. Confirm that the user can add an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_u_person.json
```

7. Confirm that the user can delete an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_u_person_2.json \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< delete_u_person.json
```

8. Confirm that the user can add an object with correct source-based classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_u_source_person.json
```

9. Confirm that there are two users - one with general classification, another with source classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

In Keycloak, update the user's attributes:
```
clearance-usa = S
nationality = USA
```

10. Confirm that the user can add an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_person.json
```

11. Confirm that the user can delete an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_person_2.json \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< delete_s_person.json
```

12. Confirm that the user can add an object with correct source-based classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_source_person.json
```

13. Confirm that the user can add an object with source S//NF classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_source_person.json
```

14. Confirm that there are five users - four with secret classification, two unclassified
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

In Keycloak, update the user's attributes. Make sure the user is not in the Data Steward Admin group:
```
clearance-usa = S
nationality = GBR
```

15. Confirm that the only **four** records are returned - the S//NF records **should not** be present
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

16. Confirm that the user **cannot** delete S//NF objects.
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< delete_snf_person.json
```

Switch the user's nationality attribute back to `USA` and confirm that Enzo5 is still present.


In Keycloak, update the user's attributes:
```
clearance-usa = U
nationality = USA
```

18. Confirm that only unclassified objects are returned
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

19. Create a new Location vertex type
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_location_vertex.json
```

20. As an unclassified user, create a new Location
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_u_location.json
```

21. As an unclassified user, create an Edge from S//NF person to U location
```shell

```

22. Bump user to secret Now try again. This should work.
23. Create a new relationship from S//NF location -> U person
24. Change the user back to U and check that
