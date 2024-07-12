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

13. Confirm that the user cannot create a S//NF document with a FVEY releaseability
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< insert_snf_fvey.json
```


13. Confirm that there are three people
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
< insert_u_location.json
```

21. As an unclassified user, create an Edge type
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_edge.json
```

22. As an unclassified user, add an edge from S//NF person (Enzo5) to U location
This command should fail.
```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_snf_person_edge.json
```

23. Bump user to secret and try again. This should work.
Keycloak attributes:
```
clearance-usa = S
nationality = USA
```

```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< create_snf_person_edge.json
```

24. Create a new S//NF location, create an edge to a S//NF person
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

25. With an unclassified user, query for edges
This command should return no edges.
Keycloak attributes:
```
clearance-usa = U
nationality = USA
```

```shell
TOKEN=$(dfdev auth token | tr -d '\n' ) \
&& http post :/api/v1/arcadedb/command/secret_people "Authorization: Bearer ${TOKEN}" \
< select_from_edge.json
```