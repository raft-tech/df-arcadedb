var editor = null;
var globalResultset = null;
var globalGraphMaxResult = 1000;
var globalCredentials = null;

/* API Gateway route */
var basePath = "/api/v1/arcadedb";

/**
 * The datetime that the access token expires. Stores this in local storage because javascript has
 * no access to HttpOnly cookies, and will be unable to refer to the expiration time in the JWT once
 * it leaves working memory.
 * */
const ACCESS_TOKEN_EXPIRES = "accessExpires";
const REFRESH_TOKEN = "refreshToken";
const REFRESH_EXPIRES = "refreshExpires";
let refreshTokenTimer;
window.addEventListener("load", onLoad, true);

/** Handle page refreshes */
function onLoad() {
  // reset refresh token timer
  let shouldLogout = false;
  const accessExpires = localStorage.getItem(ACCESS_TOKEN_EXPIRES);
  if (accessExpires && accessExpires > Date.now()) {
    refreshToken();
    const expiresSeconds = (accessExpires - Date.now()) / 1000 - 5;
    if (expiresSeconds > 0) {
      createRefreshTokenTimer(expiresSeconds);
    } else {
      shouldLogout = true;
    }
  }
  if (shouldLogout) {
    logout();
  }
}

/**
 * String 64 encodes the provided username and password. THIS DOES NOT ENCRYPT.
 * Should only be used for inner pod communication.
 */
function make_base_auth(user, password) {
  var tok = user + ':' + password;
  var hash = btoa(tok);
  return "Basic " + hash;
}

async function postJSON(data) {
  try {
    const response = await fetch(`${basePath}/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(data),
    });
    const result = await response.json();
    console.log("Success:", result);
  } catch (error) {
    console.error("Error:", error);
  }
}

async function logout() {
  // TODO call backend to revoke tokens
  // await fetch(`${basePath}/logout`, {
  //   method: "POST",
  //   headers: {
  //     "Content-Type": "application/json",
  //     //  Authorization: globalCredentials,
  //   },
  // });
  globalCredentials = null;
  localStorage.removeItem(REFRESH_EXPIRES);
  localStorage.removeItem(REFRESH_TOKEN);
  localStorage.removeItem(ACCESS_TOKEN_EXPIRES);
  // redirect to login page
  window.location.href = "/";
}

function createInactivityTimer() {
  var time;
  window.addEventListener("load", resetTimer, true);
  var events = ["mousedown", "mousemove", "keypress", "scroll", "touchstart"];
  events.forEach(function (name) {
    document.addEventListener(name, resetTimer, true);
  });
  function resetTimer() {
    clearTimeout(time);
    time = setTimeout(logout, 900000); // 15 minutes
  }
}

async function login() {
  const userName = $("#inputUserName").val().trim();
  if (userName.length == 0) return;

  const password = $("#inputUserPassword").val().trim();
  if (password.length == 0) return;

  $("#loginSpinner").show();
  const body = { username: userName, password: password };
  const oauthResponse = await fetch(`${basePath}/login`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (oauthResponse.ok) {
    const jwt = await oauthResponse.json();
    globalCredentials = `Bearer ${jwt.access_token}`;
    updateLocalStorageWithNewJwt(jwt);
    createRefreshTokenTimer(jwt.expires_in);
    createInactivityTimer();
    loadDatabaseMetadata();
  }

  updateDatabases(function () {
    initQuery();
  });
}


function updateLocalStorageWithNewJwt(jwt) {
  localStorage.setItem(REFRESH_TOKEN, jwt.refresh_token);
  localStorage.setItem(
    REFRESH_EXPIRES,
    getRefreshExpiration(jwt.refresh_expires_in)
  );
  // TODO get access token expiration from JWT and save to local storage.
  // TODO update to use httpOnly cookies. Javascript has no access to them
  localStorage.setItem(ACCESS_TOKEN_EXPIRES, Date.now() + jwt.expires_in * 1000);
}

function getRefreshExpiration(expiresIn) {
  return Date.now() + expiresIn * 1000;
}

function createRefreshTokenTimer(expiresSeconds) {
  if (expiresSeconds) {
    clearTimeout(refreshTokenTimer);
    refreshTokenTimer = setTimeout(refreshToken, (expiresSeconds - 5) * 1000);
  }
}

async function refreshToken() {
  console.log("refresh token");
  const oauthResponse = await fetch(
    `${basePath}/refreshToken`,
    {
      method: "POST",
      headers: {
        "Content-Type": "text/plain",
      },
      body: 
        JSON.stringify({refreshToken: localStorage.getItem(REFRESH_TOKEN)}),
      
    }
  );
  if (oauthResponse.ok) {
    const jwt = await oauthResponse.json();
    updateLocalStorageWithNewJwt(jwt);
    createRefreshTokenTimer(jwt.expires_in);

    if (globalCredentials == undefined) {
      globalCredentials = `Bearer ${jwt.access_token}`;
      updateDatabases(function () {
        initQuery();
      });
    } else {
      globalCredentials = `Bearer ${jwt.access_token}`;
    }
  }
}

function showLoginPopup(){
  $("#loginPopup").modal("show");
}

function editorFocus(){
  let value = editor.getValue();
  editor.setValue("");
  editor.setValue(value);

  editor.setCursor(editor.lineCount(), 0);
  editor.focus();
}

function updateDatabases( callback ){
  let selected = getCurrentDatabase();
  if (selected == null || selected == "")
    selected = globalStorageLoad("database.current");

  jQuery.ajax({
    type: "GET",
    url: basePath + "/databasesInfo",
    beforeSend: function (xhr){
      xhr.setRequestHeader('Authorization', globalCredentials);
    }
  })
  .done(function(data){
    let json = JSON.parse(JSON.stringify(data));
    
    if (typeof json == "object" && json["result"] == 'ok') {
      // no data returned, the user doesn't have access to any databases
    } else {
      // valid response, parse the databases the user has access to

      let databases = "";
      for( let i in data.result ){
        let dbName = data.result[i].name;
        let classification = data.result[i].classification || "";
        databases += `<option value='${dbName}'><b>(${classification})</b> ${dbName}</option>`;
      }
      $("#inputDatabase").html(databases);
  
      if( selected != null && selected != "" )
        $("#inputDatabase").val(selected);
  
      $("#currentDatabase").html( getCurrentDatabase() );

      getClassificationForDatabase(getCurrentDatabase()).then((classification) => {
        $("#lblCurrentDatabaseClassification").html( classification );
      });
    }

    $("#user").html(data.user);

    $("#loginPopup").modal("hide");
    $("#welcomePanel").hide();
    $("#studioPanel").show();

    displaySchema();
    displayDatabaseSettings();

    if( callback )
      callback();
  })
  .fail(function( jqXHR, textStatus, errorThrown ){
    globalNotifyError( jqXHR.responseText );
  })
  .always(function(data) {
    $("#loginSpinner").hide();
  });
}

function createDatabase(){
    // <input onkeydown='if (event.which === 13) Swal.clickConfirm()' id='inputCreateDatabaseName'>
  let html = `
  <div style='text-align: left'>
  <div style="padding-bottom: 10px">
  <b>Name:  </b><input id='inputCreateDatabaseName' type='text'></br>
  </div>
  <div style="padding-bottom: 10px">
  <b>Classification:  </b><input id='inputClassification'></br>
  </div>
  <div style="padding-bottom: 10px">
  <b>Owner:  </b><input id='inputOwner'>
  </div>
  <p style="margin-bottom: 5px; font-weight: bold;">Please select database visibility:</p>
  <input type="radio" id="rbPublic" name="publicPrivate" value="public">
  <label for="rbPublic">Public</label><br>
  <input type="radio" id="rbPrivate" name="publicPrivate" value="private">
  <label for="rbPrivate">Private</label><br>
  <label for="import"><b>Import WIP SDL Ontology:</b></label>
  <input type="checkbox" id="chkImport" name="chkImport" value="import">
  <label for="validation"><b>Enable SDL Classification Data Access Enforcement:</b></label>
  <input type="checkbox" id="chkValidation" name="chkValidation" value="enabled" checked oninput=onValidationEnabledChange()>
  </div>
  `;
  Swal.fire({
    title: 'Create a new database',
    html: html,
    inputAttributes: {
      autocapitalize: 'off'
    },
    confirmButtonColor: '#3ac47d',
    cancelButtonColor: 'red',
    showCancelButton: true,
    confirmButtonText: 'Send',
  }).then((result) => {
    if (result.value) {
      let database = encodeURI( $("#inputCreateDatabaseName").val().trim() );
      let owner = encodeURI( $("#inputOwner").val().trim() );
      let visibility = "public";

      var ele = document.getElementsByName('publicPrivate');
 
      for (i = 0; i < ele.length; i++) {
          if (ele[i].checked)
              visibility = ele[i].value;
      }

      let classification = encodeURI( $("#inputClassification").val().trim() );

      var chkImport = document.getElementsByName('chkImport');
      let importOntology = chkImport[0].checked;

      var chkValidation = document.getElementsByName('chkValidation');
      let enableClassificationValidation = chkValidation[0].checked;

      var options = {
        owner: owner,
        visibility: visibility,
        classification: classification,
        importOntology: importOntology,
        classificationValidationEnabled: enableClassificationValidation
      }

      if ( database == "" ){
        globalNotify( "Error", "Database name empty", "danger");
        return;
      }

      jQuery.ajax({
        type: "POST",
        url: basePath + "/server",

        // TODO update with new options props.
        data: "{ 'command': 'create database " + database + "', options: "+ JSON.stringify(options) + " }",
        beforeSend: function (xhr){
          xhr.setRequestHeader('Authorization', globalCredentials);
        }
      })
      .done(function(data){
        $("#inputDatabase").val(database);
        updateDatabases();
      })
      .fail(function( jqXHR, textStatus, errorThrown ){
        globalNotifyError( jqXHR.responseText );
      });
    }
  });

  $("#inputCreateDatabaseName").focus();
}

/**
 * Set the database classification to U, and block updates when classification validation is disabled.
 */
function onValidationEnabledChange() {
  var chkValidation = document.getElementsByName('chkValidation');
  let enableClassificationValidation = chkValidation[0].checked;

  if (!enableClassificationValidation) {
    $("#inputClassification").val('U');
    $("#inputClassification")[0].readOnly = true;
  } else {
    $("#inputClassification")[0].readOnly = false;
  }
}

function dropDatabase(){
  let database = escapeHtml( getCurrentDatabase() );
  if( database == "" ){
    globalNotify( "Error", "Database not selected", "danger");
    return;
  }

  globalConfirm("Drop database", "Are you sure you want to drop the database '"+database+"'?<br>WARNING: The operation cannot be undone.", "warning", function(){
    jQuery.ajax({
      type: "POST",
      url: basePath + "/server",
      data: "{ 'command': 'drop database " + database + "' }",
      beforeSend: function (xhr){
        xhr.setRequestHeader('Authorization', globalCredentials);
      }
    })
    .done(function(data){
      updateDatabases();
    })
    .fail(function( jqXHR, textStatus, errorThrown ){
      globalNotifyError( jqXHR.responseText );
    });
  });
}

function resetDatabase(){
  let database = escapeHtml( getCurrentDatabase() );
  if( database == "" ){
    globalNotify( "Error", "Database not selected", "danger");
    return;
  }

  globalConfirm("Reset database", "Are you sure you want to reset the database '"+database+"' (All data will be deleted)?<br>WARNING: The operation cannot be undone.", "warning", function(){
    jQuery.ajax({
      type: "POST",
      url: "/api/v1/server",
      data: "{ 'command': 'drop database " + database + "' }",
      beforeSend: function (xhr){
        xhr.setRequestHeader('Authorization', globalCredentials);
      }
    })
    .done(function(data){
      jQuery.ajax({
        type: "POST",
        url: "/api/v1/server",
        data: "{ 'command': 'create database " + database + "' }",
        beforeSend: function (xhr){
          xhr.setRequestHeader('Authorization', globalCredentials);
        }
      })
      .done(function(data){
        $("#inputDatabase").val(database);
        updateDatabases();
      })
      .fail(function( jqXHR, textStatus, errorThrown ){
        globalNotifyError( jqXHR.responseText );
      });
    })
    .fail(function( jqXHR, textStatus, errorThrown ){
      globalNotifyError( jqXHR.responseText );
    });
  });
}

function backupDatabase(){
  let database = getCurrentDatabase();
  if( database == "" ){
    globalNotify( "Error", "Database not selected", "danger");
    return;
  }

  globalConfirm("Backup database", "Are you sure you want to backup the database '"+database+"'?<br>The database archive will be created under the 'backup' directory of the server.", "info", function(){
    jQuery.ajax({
      type: "POST",
      url: basePath + "/command/" + database,
      data: JSON.stringify(
        {
          language: "sql",
          command: "backup database",
          serializer: "record"
        }
      ),
      beforeSend: function (xhr){
        xhr.setRequestHeader('Authorization', globalCredentials);
      }
    })
    .done(function(data){
      globalNotify( "Backup completed", "File: " + escapeHtml( data.result[0].backupFile ), "success");
    })
    .fail(function( jqXHR, textStatus, errorThrown ){
      globalNotifyError( jqXHR.responseText );
    });
  });
}

function dropProperty(type, property){
  let database = getCurrentDatabase();
  if( database == "" ){
    globalNotify( "Error", "Database not selected", "danger");
    return;
  }

  globalConfirm("Drop property", "Are you sure you want to drop the property '"+property+"' on type '"+type+"'?<br>WARNING: The operation cannot be undone.", "warning", function(){
    jQuery.ajax({
      type: "POST",
      url: basePath + "/command/" + database,
      data: JSON.stringify(
        {
          language: "sql",
          command: "drop property `" + type + "`.`" + property + "`",
          serializer: "record"
        }
      ),
      beforeSend: function (xhr){
        xhr.setRequestHeader('Authorization', globalCredentials);
      }
    })
    .done(function(data){
      updateDatabases();
    })
    .fail(function( jqXHR, textStatus, errorThrown ){
      globalNotifyError( jqXHR.responseText );
    });
  });
}

function dropIndex(indexName){
  let database = getCurrentDatabase();
  if( database == "" ){
    globalNotify( "Error", "Database not selected", "danger");
    return;
  }

  globalConfirm("Drop index", "Are you sure you want to drop the index '"+indexName+"'?<br>WARNING: The operation cannot be undone.", "warning", function(){
    jQuery.ajax({
      type: "POST",
      url: basePath + "/command/" + database,
      data: JSON.stringify(
        {
          language: "sql",
          command: "drop index `" + indexName + "`",
          serializer: "record"
        }
      ),
      beforeSend: function (xhr){
        xhr.setRequestHeader('Authorization', globalCredentials);
      }
    })
    .done(function(data){
      updateDatabases();
    })
    .fail(function( jqXHR, textStatus, errorThrown ){
      globalNotifyError( jqXHR.responseText );
    });
  });
}

function getCurrentDatabase(){
  let db = $("#inputDatabase").val();
  return db != null ? db.trim() : null;
}

function setCurrentDatabase( dbName ) {
  let data = getDatabaseMetadata().then((data) => {

    for ( let i in data ) {
      if ( data[i].name == $("#inputDatabase").val()) {
        $("#lblCurrentDatabaseClassification").html( escapeHtml(data[i].classification || "") );
      }
    }
  });

  $("#currentDatabase").html( dbName );
  $("#inputDatabase").val( dbName );
  globalStorageSave("database.current", dbName);
  loadDatabaseMetadata();
}

function getQueryHistory(){
  let queryHistory = globalStorageLoad("database.query.history");
  if( queryHistory == null )
    queryHistory = [];
  else {
    try{
      queryHistory = JSON.parse(queryHistory);
    } catch(e) {
      // RESET HISTORY
      globalStorageSave("database.query.history", "[]");
      queryHistory = [];
    }
  }

  return queryHistory;
}

function loadQueryHistory(){
  $("#inputHistory").html("");
  $("#inputHistory").append( "<option value='-1'>Query History</option>" );

  let queryHistory = getQueryHistory();
  if( queryHistory != null && queryHistory.length > 0 ){
    let database = escapeHtml( getCurrentDatabase() );
    for( let index = 0; index < queryHistory.length; ++index ) {
      let q = queryHistory[index];
      if( q != null && q.d == database && q.l != null && q.c != null )
        $("#inputHistory").append( "<option value='" + index + "'>("+q.l+") "+q.c+"</option>" );
    }
  }
}

function copyQueryFromHistory(){
  let index = $("#inputHistory").val();
  if( index == -1 )
    return;

  if( index != "" ){
    let queryHistory = getQueryHistory();
    let q = queryHistory[index];
    if( q != null ) {
       setCurrentDatabase( q.d );
       $("#inputLanguage").val( q.l );
      editor.setValue( q.c );
    }
  }

  $("#inputHistory").val(-1);
}

function executeCommand(language, query){
  globalResultset = null;

  if( language != null )
    $("#inputLanguage").val( language );
  else
    language = $("#inputLanguage").val();

  if( query != null )
    editor.setValue( query );
  else {
    query = editor.getSelection();
    if( query == null || query == "" )
      query = editor.getValue();
  }

  let database = getCurrentDatabase();

  if( database == "" )
    return;
  if( escapeHtml( $("#inputLanguage").val() ) == "" )
    return;
  if( escapeHtml( query ) == "" )
    return;

  globalActivateTab("tab-query");

  let activeTab = $("#tabs-command .active").attr("id");
  if( activeTab == "tab-graph-sel" )
    executeCommandGraph();
  else
    executeCommandTable();

  let queryHistory = getQueryHistory();

  for( index in queryHistory ){
    let q = queryHistory[index];
    if( q == null ||
        ( q.d == database &&
          q.l == language &&
          q.c == query ) ) {
      // RE-EXECUTED OLD QUERY, REMOVE OLD ENTRY AND INSERT AT THE TOP OF THE LIST
      queryHistory.splice(index,1);
    }
  }

  // REMOVE OLD QUERIES
  while( queryHistory.length > 25 )
    queryHistory.pop();

  queryHistory = [ {"d": database, "l": language, "c": query} ].concat(queryHistory);
  globalStorageSave("database.query.history", JSON.stringify( queryHistory ) );

  loadQueryHistory();
}

function executeCommandTable(){
  let database = getCurrentDatabase();
  let language = escapeHtml( $("#inputLanguage").val() );

  let command = editor.getSelection();
  if( command == null || command == "" )
    command = editor.getValue();
  command = escapeHtml( command );

  let limit = parseInt( $("#inputLimit").val() );
  let profileExecution = $('#profileCommand').prop('checked') ? "detailed" : "basic";

  $("#executeSpinner").show();

  let beginTime = new Date();

  jQuery.ajax({
    type: "POST",
    url: basePath + "/command/" + database,
    data: JSON.stringify(
      {
        language: language,
        command: command,
        limit: limit,
        profileExecution: profileExecution,
        serializer: "studio"
      }
    ),
    beforeSend: function (xhr){
      xhr.setRequestHeader('Authorization', globalCredentials);
    }
  })
  .done(function(data){
    let elapsed = new Date() - beginTime;
    $("#result-elapsed").html( elapsed );

    $("#result-num").html( data.result.records.length );
    $("#resultJson").val( JSON.stringify(data, null, 2) );
    $("#resultExplain").val( data.explain != null ? data.explain : "No profiler data found" );

    globalResultset = data.result;
    globalCy = null;
    renderTable();
  })
  .fail(function( jqXHR, textStatus, errorThrown ){
    globalNotifyError( jqXHR.responseText );
  })
  .always(function(data) {
    $("#executeSpinner").hide();
  });
}

function executeCommandGraph(){
  let database = getCurrentDatabase();
  let language = escapeHtml( $("#inputLanguage").val() );

  let command = editor.getSelection();
  if( command == null || command == "" )
    command = editor.getValue();
  command = escapeHtml( command );

  let limit = parseInt( $("#inputLimit").val() );
  let profileExecution = $('#profileCommand').prop('checked') ? "detailed" : "basic";

  $("#executeSpinner").show();

  let beginTime = new Date();

  jQuery.ajax({
    type: "POST",
    url: basePath + "/command/" + database,
    data: JSON.stringify(
      {
        language: language,
        command: command,
        limit: limit,
        profileExecution: profileExecution,
        serializer: "studio"
      }
    ),
    beforeSend: function (xhr){
      xhr.setRequestHeader('Authorization', globalCredentials);
    }
  })
  .done(function(data){
    let elapsed = new Date() - beginTime;
    $("#result-elapsed").html( elapsed );

    $("#result-num").html( data.result.records.length );
    $("#resultJson").val( JSON.stringify(data, null, 2) );
    $("#resultExplain").val( data.explain != null ? data.explain : "'" );

    globalResultset = data.result;
    globalCy = null;

    let activeTab = $("#tabs-command .active").attr("id");

    if( data.result.vertices.length == 0 && data.result.records.length > 0 ){
      if( activeTab == "tab-table-sel" )
        renderTable();
      else
        globalActivateTab("tab-table");
    } else {
      if( activeTab == "tab-graph-sel" )
        renderGraph();
      else
        globalActivateTab("tab-graph");
    }

    // FORCE RESET OF THE SEARCH FIELD
    $("#inputGraphSearch").val("");
  })
  .fail(function( jqXHR, textStatus, errorThrown ){
    globalNotifyError( jqXHR.responseText );
  })
  .always(function(data) {
    $("#executeSpinner").hide();
  });
}

function displaySchema(){
  let database = getCurrentDatabase();
  if( database == null || database == "" )
    return;

  jQuery.ajax({
    type: "POST",
    url: basePath + "/query/" + database,
    data: JSON.stringify(
      {
        language: "sql",
        command: "select from schema:types"
      }
    ),
    beforeSend: function (xhr){
      xhr.setRequestHeader('Authorization', globalCredentials);
    }
  })
  .done(function(data){
    let tabVHtml = "";
    let tabEHtml = "";
    let tabDHtml = "";

    let panelVHtml = "";
    let panelEHtml = "";
    let panelDHtml = "";

    // BUILD SUB TYPES
    let subTypes = {};
    for( let i in data.result ){
      let row = data.result[i];

      for( ptidx in row.parentTypes ) {
        let pt = row.parentTypes[ptidx];

        let array = subTypes[ pt ];
        if( array == null ) {
          array = [];
          subTypes[ pt ] = array;
        }
        array.push( row.name );
      }
    }

    for( let i in data.result ){
      let row = data.result[i];
      let tabName = row.name.replaceAll(":", "-");

      let tabHtml = "<li class='nav-item' style='height: 32px; width: 240px'><a data-toggle='tab' href='#tab-" + tabName + "' class='nav-link vertical-tab" + (i == 0 ? " active show" : "");
      tabHtml += "' id='tab-" + tabName + "-sel'>" + row.name + "</a></li>";

      let panelHtml = "<div class='tab-pane fade"+(i == 0 ? " active show" : "") +"' id='tab-"+tabName+"' role='tabpanel'>";

      panelHtml += "<h3>" + row.name + " <span style='font-size: 60%'>("+ row.records +" records)</span></h3>";
      if( row.parentTypes != "" ){
        panelHtml += "Super Types: <b>";
        for( ptidx in row.parentTypes ) {
          if( ptidx > 0 )
            panelHtml += ", ";
          let pt = row.parentTypes[ptidx];
          let ptName = pt.replaceAll(":", "-");

          panelHtml += "<b><a href='#' onclick=\"globalActivateTab('tab-"+ptName+"')\">" + pt + "</a></b>";
        }
        panelHtml += "</b>";
      }

      let typeSubTypes = subTypes[row.name];
      if( typeSubTypes != null ){
        panelHtml += "<br>Sub Types: <b>";
        for( stidx in typeSubTypes ) {
          if( stidx > 0 )
            panelHtml += ", ";
          let st = typeSubTypes[stidx];
          let stName = st.replaceAll(":", "-");

          panelHtml += "<b><a href='#' onclick=\"globalActivateTab('tab-"+stName+"')\">" + st + "</a></b>";
        }
        panelHtml += "</b>";
      }

      panelHtml += "<br>";

      if( row.indexes != "" ){
        panelHtml += "<br><h6>Indexes</h6>";
        panelHtml += "<div class='table-responsive'>";
        panelHtml += "<table class='table table-striped table-sm' style='border: 0px; width: 100%'>";
        panelHtml += "<thead><tr><th scope='col'>Name</th>";
        panelHtml += "<th scope='col'>Defined In</th>";
        panelHtml += "<th scope='col'>Properties</th>";
        panelHtml += "<th scope='col'>Type</th>";
        panelHtml += "<th scope='col'>Unique</th>";
        panelHtml += "<th scope='col'>Automatic</th>";
        panelHtml += "<th scope='col'>Actions</th>";
        panelHtml += "<tbody>";

        panelHtml += renderIndexes( row, data.result );

        panelHtml += "</tbody></table></div>";
      }

      panelHtml += "<br><h6>Properties</h6>";
      //panelHtml += "<button class='btn btn-pill' onclick='createProperty()'><i class='fa fa-plus'></i> Create Property</button>";
      panelHtml += "<div class='table-responsive'>";
      panelHtml += "<table class='table table-striped table-sm' style='border: 0px; width: 100%'>";
      panelHtml += "<thead><tr><th scope='col'>Name</th>";
      panelHtml += "<th scope='col'>Defined In</th>";
      panelHtml += "<th scope='col'>Type</th>";
      panelHtml += "<th scope='col'>Mandatory</th>";
      panelHtml += "<th scope='col'>Not Null</th>";
      panelHtml += "<th scope='col'>Read Only</th>";
      panelHtml += "<th scope='col'>Default Value</th>";
      panelHtml += "<th scope='col'>Min</th>";
      panelHtml += "<th scope='col'>Max</th>";
      panelHtml += "<th scope='col'>Regexp</th>";
      panelHtml += "<th scope='col'>Indexes</th>";
      panelHtml += "<th scope='col'>Actions</th>";
      panelHtml += "<tbody>";

      panelHtml += renderProperties( row, data.result );

      panelHtml += "</tbody></table></div>";

      panelHtml += "<br><h6>Actions</h6>";
      panelHtml += "<ul>";
      panelHtml += "<li><a class='link' href='#' onclick='executeCommand(\"sql\", \"select from `"+row.name+"` limit 30\")'>Display the first 30 records of "+row.name+"</a>";
      if( row.type == "vertex" )
        panelHtml += "<li><a class='link' href='#' onclick='executeCommand(\"sql\", \"select *, bothE() as `@edges` from `"+row.name+"` limit 30\")'>Display the first 30 records of "+row.name+" together with all the vertices that are directly connected</a>";
      panelHtml += "<li><a class='link' href='#' onclick='executeCommand(\"sql\", \"select count(*) from `"+row.name+"`\")'>Count the records of type "+row.name+"</a>";
      panelHtml += "</ul>";

      panelHtml += "</div>";

      if( row.type == "vertex" ) {
        tabVHtml += tabHtml;
        panelVHtml += panelHtml;
      } else if( row.type == "edge" ) {
        tabEHtml += tabHtml;
        panelEHtml += panelHtml;
      } else {
        tabDHtml += tabHtml;
        panelDHtml += panelHtml;
      }
    }

    $("#vTypesTabs").html(tabVHtml);
    $("#eTypesTabs").html(tabEHtml);
    $("#dTypesTabs").html(tabDHtml);

    $("#vTypesPanels").html(panelVHtml != "" ? panelVHtml : "Not defined." );
    $("#eTypesPanels").html(panelEHtml != "" ? panelEHtml : "Not defined." );
    $("#dTypesPanels").html(panelDHtml != "" ? panelDHtml : "Not defined." );
  })
  .fail(function( jqXHR, textStatus, errorThrown ){
    globalNotifyError( jqXHR.responseText );
  })
  .always(function(data) {
    $("#executeSpinner").hide();
  });
}

function findTypeInResult(name, results ){
  for( i in results )
    if( results[i].name == name )
      return results[i];
  return null;
}

function renderProperties(row, results ){
  let panelHtml = "";

  for( let k in row.properties ) {
    let property = row.properties[k];
    panelHtml += "<tr><td>"+property.name+"</td><td>" + row.name + "</td><td>" + property.type + "</td>";

    panelHtml += "<td>" + ( property.mandatory ? true : false ) + "</td>";
    panelHtml += "<td>" + ( property.notNull ? true : false ) + "</td>";
    panelHtml += "<td>" + ( property.readOnly ? true : false ) + "</td>";
    panelHtml += "<td>" + ( property["default"] != null ? property["default"] : "" ) + "</td>";
    panelHtml += "<td>" + ( property.min != null ? property.min : "" ) + "</td>";
    panelHtml += "<td>" + ( property.max != null ? property.max : "" ) + "</td>";
    panelHtml += "<td>" + ( property.regexp != null ? property.regexp : "" ) + "</td>";

    let totalIndexes = 0;
    if( row.indexes != null && row.indexes.length > 0 ){
      for( i in row.indexes ){
        if( row.indexes[i].properties.includes( property.name ) )
          ++totalIndexes;
      }
    }

    panelHtml += "<td>"+(totalIndexes > 0 ? totalIndexes : "None")+"</td>";
    panelHtml += "<td><button class='btn btn-pill' onclick='dropProperty(\""+row.name+"\", \""+property.name+"\")'><i class='fa fa-minus'></i> Drop Property</button></td></tr>";

    if( property.custom != null && Object.keys( property.custom ).length > 0 ) {
      panelHtml += "<td></td>";
      panelHtml += "<td colspan='10'><b>Custom Properties</b><br>";
      panelHtml += "<div class='table-responsive'>";
      panelHtml += "<table style='width: 100%'>";
      for( c in property.custom )
        panelHtml += "<tr><td width='30%'>" + c + "</td><td width='70%'>" + property.custom[c] + "</td></tr>";
      panelHtml += "</table></div></td>";
    }
  }

  if( row.parentTypes != "" ){
    for( ptidx in row.parentTypes ) {
      let pt = row.parentTypes[ptidx];
      let type = findTypeInResult( pt, results );
      panelHtml += renderProperties( type, results );
    }
  }

  return panelHtml;
}

function renderIndexes(row, results ){
  let panelHtml = "";

  for( let k in row.indexes ) {
    let index = row.indexes[k];
    panelHtml += "<tr><td>"+index.name+"</td><td>" + index.typeName + "</td>";
    panelHtml += "<td>" + index.properties + "</td>";
    panelHtml += "<td>" + index.type + "</td>";

    panelHtml += "<td>" + ( index.unique ? true : false ) + "</td>";
    panelHtml += "<td>" + ( index.automatic ? true : false ) + "</td>";
    panelHtml += "<td><button class='btn btn-pill' onclick='dropIndex(\"" + index.name + "\")'><i class='fa fa-minus'></i> Drop Index</button></td></tr>";
  }
  return panelHtml;
}

function displayDatabaseSettings(){
  let database = getCurrentDatabase();
  if( database == null || database == "" )
    return;

  jQuery.ajax({
    type: "POST",
    url: basePath + "/query/" + database,
    data: JSON.stringify(
      {
        language: "sql",
        command: "select expand( settings ) from schema:database"
      }
    ),
    beforeSend: function (xhr){
      xhr.setRequestHeader('Authorization', globalCredentials);
    }
  })
  .done(function(data){
    if ( $.fn.dataTable.isDataTable( '#dbSettings' ) )
      try{ $('#dbSettings').DataTable().destroy(); $('#serverMetrics').empty(); } catch(e){};

    var tableRecords = [];

    for( let i in data.result ){
      let row = data.result[i];

      let record = [];
      record.push( row.key );
      record.push( row.value );
      record.push( row.description );
      record.push( row.default );
      record.push( row.overridden );
      tableRecords.push( record );
    }

   $("#dbSettings").DataTable({
      paging: false,
      ordering: false,
      autoWidth: false,
      columns: [
        {title: "Key", width: "25%"},
        {title: "Value", width: "20%",
         render: function ( data, type, row) {
           return "<a href='#' onclick='updateDatabaseSetting(\""+row[0]+"\", \""+row[1]+"\")' style='color: green;'><b>"+data+"</b></a>";
         }},
        {title: "Description", width: "33%"},
        {title: "Default", width: "15%"},
        {title: "Overridden", width: "7%"},
      ],
      data: tableRecords,
    });
  })
  .fail(function( jqXHR, textStatus, errorThrown ){
    globalNotifyError( jqXHR.responseText );
  })
  .always(function(data) {
    $("#executeSpinner").hide();
  });
}

function updateDatabaseSetting(key, value){
  let html = "<b>" + key + "</b> = <input id='updateSettingInput' value='"+value+"'>";
  html += "<br><p><i>The setting will be saved in the database configuration.</i></p>";

  Swal.fire({
    title: "Update Database Setting",
    html: html,
    showCancelButton: true,
    width: 600,
    confirmButtonColor: '#3ac47d',
    cancelButtonColor: 'red',
  }).then((result) => {
    if (result.value) {
      jQuery.ajax({
       type: "POST",
       url: basePath + "/server",
       data: JSON.stringify(
         {
           language: "sql",
           command: "set database setting " + getCurrentDatabase() + " " + key + " " +$("#updateSettingInput").val()
         }
       ),
       beforeSend: function (xhr){
         xhr.setRequestHeader('Authorization', globalCredentials);
       }
      })
      .done(function(data){
        if( data.error ) {
          $("#authorizationCodeMessage").html(data.error);
          return false;
        }
        displayDatabaseSettings();
        return true;
      });
    }
  });
}

function loadDatabaseMetadata(){
  getDatabaseMetadata().then((data) => {
    setDatabaseMetadata(data)
  });
}

async function getClassificationForDatabase(database) {
  database = escapeHtml(database?.trim());
  if (database != null && database != "") {
    let data = await getDatabaseMetadata();
    for ( let i in data ) {
      if ( data[i].name == escapeHtml(database)) {
        return data[i].classification || "";
      }
    }
  }
  return "";
}

async function getDatabaseMetadata() {

  const dataResponse = await fetch(`${basePath}/databasesInfo`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      'Authorization': globalCredentials
    }
  });
  if (dataResponse.ok) {
    const data = await dataResponse.json();
    return data.result;
  }
}

function setDatabaseMetadata(data) {
  for ( let i in data ) {
    if ( data[i].name == escapeHtml($("#inputDatabase").val())) {
        $("#lblClassification").text( data[i].classification || "" );
        $("#lblOwner").text( data[i].owner || "" );
        $("#lblPublic").text( data[i].isPublic || "" );
        $("#lblCreatedBy").text( data[i].createdBy || "" );
        $("#lblCreatedDate").text( data[i].createdDateTime || "" );
        $("#lblClassificationValidationEnabled").text( data[i].classificationValidationEnabled);
      }
    }
}