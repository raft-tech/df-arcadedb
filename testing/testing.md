# Testing


In Keycloak, add the following attributes to the admin user:
```
clearance_usa = U
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

8. Confirm that there is one user
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

In Keycloak, update the user's attributes:
```
clearance_usa = S
nationality = USA
```
9. Confirm that the user can add an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_person.json
```

10. Confirm that the user can delete an object with correct general classification
```shell 
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_s_person_2.json \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< delete_s_person.json
```

11. Confirm that the user can add an object with source S//NF classification
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_person.json
```

12. Confirm that the user cannot create a S//NF document with a FVEY releaseability (FAILED)
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_fvey.json
```


13. Confirm that there are three people (FAILED)
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

In Keycloak, update the user's attributes. Make sure the user is not in the Data Steward Admin group:
```
clearance_usa = S
nationality = GBR
```

14. Confirm that only **two** records are returned - the S//NF records **should not** be present (FAILED)
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

15. Confirm that the user **cannot** delete S//NF objects (FAILED)
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< delete_snf_person.json
```


15. Confirm that the user **cannot** add S//NF objects (FAILED)
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_person.json
```

Switch the user's nationality attribute back to `USA` and confirm that Enzo3 (S//NF) is still present. (FAILED)


In Keycloak, update the user's attributes:
```
clearance_usa = U
nationality = USA
```

16. Confirm that only unclassified objects are returned
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_people.json
```

17. Create a new Location vertex type
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_location_vertex.json
```

18. As an unclassified user, create a new Location
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_u_location.json
```

19. As an unclassified user, create an Edge type
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_edge.json
```

20. As an unclassified user, add an edge from S//NF person (Enzo3) to U location - PROBABLY BROKEN - MISSING COMPONENT STILL SAVED(******)
This command should fail.
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_snf_person_edge.json
```

21. Bump user to secret and try again. This should work. - PROBABLY BROKEN - MISSING COMPONENT STILL SAVED(******)
Keycloak attributes:
```
clearance_usa = S
nationality = USA
```

```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_snf_person_edge.json
```

22. Create a new S//NF location, create an edge to a S//NF person
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_location.json
```

```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_snf_edge.json
```

23. With an unclassified user, query for edges -- this command should return no edges. (FAILED)
Keycloak attributes:
```
clearance_usa = U
nationality = USA
```

```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_edge.json
```