var globalResultset = null;
var globalBgColors = ['aqua', 'orange', 'green', 'purple', 'lime', 'teal', 'maroon', 'navy', 'olive', 'silver', 'blue', 'yellow', 'fuchsia', 'gray', 'white', 'black'];
var globalFgColors = ['black', 'black', 'white', 'white', 'black', 'black', 'white', 'white', 'black', 'black', 'white', 'black', 'black', 'white', 'black', 'white'];
var globalRenderedVerticesRID = {};
var globalTotalEdges = 0;
var globalLastColorIndex = 0;
var globalGraphPropertiesPerType = {};
var globalLayout = null;
var globalCy = null;
var globalSelected = null;
var globalEnableElementPanel = true;
// var globalGraphSettings = {
//   graphSpacing: 50,
//   cumulativeSelection: false,
//   types: {},
// };
// i know this is not ideal to embed it here but i don't know how to easirly reference this json from another file
var globalGraphSettings = JSON.parse(`
{
  "graphSpacing": 50,
  "cumulativeSelection": false,
  "types": {
    "cco-IntentionalAct": {
      "labelColor": "black",
      "shapeColor": "aqua",
      "element": "v",
      "icon": "fas fa-chess",
      "labelPosition": "bottom center"
    },
    "bfo-temporal_region": {
      "labelColor": "black",
      "shapeColor": "orange",
      "element": "v",
      "icon": "fas fa-calendar",
      "labelPosition": "bottom center"
    },
    "cco-Target": {
      "labelColor": "black",
      "shapeColor": "green",
      "element": "v",
      "icon": "fas fa-crosshairs",
      "labelPosition": "bottom center",
      "iconColor": "purple"
    },
    "todo-connected_to": {
      "element": "e"
    },
    "cco-DesignativeInformationContentEntity": {
      "labelColor": "black",
      "shapeColor": "purple",
      "element": "v",
      "icon": "fas fa-tag",
      "labelPosition": "bottom center",
      "labelText": "cco-has_text_value"
    },
    "cco-ActOfCommunication": {
      "labelColor": "black",
      "shapeColor": "lime",
      "element": "v",
      "icon": "fas fa-comment",
      "labelPosition": "bottom center",
      "iconColor": "teal"
    },
    "cco-designated_by": {
      "element": "e"
    },
    "cco-has_object": {
      "element": "e"
    },
    "cco-Agent": {
      "labelColor": "black",
      "shapeColor": "teal",
      "element": "v",
      "icon": "far fa-user",
      "labelPosition": "bottom center",
      "iconColor": "orange"
    },
    "bfo-exists_at": {
      "element": "e"
    },
    "cco-has_agent": {
      "element": "e"
    },
    "bfo-temporal_instant": {
      "labelColor": "black",
      "shapeColor": "maroon",
      "element": "v",
      "icon": "fas fa-calendar-alt",
      "labelPosition": "bottom center",
      "labelText": "cco-has_date_value"
    },
    "bfo-has_last_instant": {
      "element": "e"
    },
    "cco-DescriptiveInformationContentEntity": {
      "labelColor": "black",
      "shapeColor": "navy",
      "element": "v",
      "icon": "fas fa-align-left",
      "labelPosition": "bottom center",
      "iconColor": "blue",
      "labelText": "cco-has_text_value"
    },
    "cco-DesignativeName": {
      "labelColor": "black",
      "shapeColor": "olive",
      "element": "v",
      "icon": "fas fa-tag",
      "labelPosition": "bottom center",
      "labelText": "cco-has_text_value"
    },
    "cco-GeospatialLocation": {
      "labelColor": "black",
      "shapeColor": "silver",
      "element": "v",
      "icon": "fas fa-map-marker",
      "labelPosition": "bottom center",
      "iconColor": "lime"
    },
    "cco-described_by": {
      "element": "e"
    },
    "bfo-located_in": {
      "element": "e"
    },
    "cco-ActOfEstimation": {
      "labelColor": "black",
      "shapeColor": "blue",
      "element": "v",
      "icon": "fas fa-weight",
      "labelPosition": "bottom center"
    },
    "cco-Objective": {
      "labelColor": "black",
      "shapeColor": "yellow",
      "element": "v",
      "icon": "fas fa-bullseye",
      "labelPosition": "bottom center",
      "iconColor": "aqua"
    }
  }
}
`)

function toggleSidebar(){
  $("#graphPropertiesPanel").toggleClass("collapsed");
  $("#graphMainPanel").toggleClass("col-md-12 col-md-9");
}

function importGraph(format){
  var html = "<center><h5>Copy below the "+format.toUpperCase()+" of the graph to import or upload a file</h5>";
  html += "<input id='uploadFile' type='file' accept='.json, .txt, .js' />";
  html += "<center><textarea id='importContent' rows='30' cols='75'></textarea><br>";
  html += "<button id='importGraph' type='button' class='btn btn-primary'><i class='fa fa-upload'></i> Import the graph</button><br>";
  html += "<div id='importStatus'><span>&nbsp;</span><div></center>";

  $("#popupBody").html(html);

  $("#importGraph").on( "click", function(){
    switch( format ) {
      case "json":
        globalResultset = JSON.parse( $("#importContent").val() );

        globalGraphSettings = globalResultset.settings;
        delete globalResultset.settings;

        renderGraph();
        break;
    }
    renderGraph();
    $('#popup').modal("hide");
  });

  const reader = new FileReader();
  const fileUploader = document.getElementById('uploadFile');
  fileUploader.addEventListener('change', (event) => {
    const files = event.target.files;
    const file = files[0];
    console.log('files', files);

    if (file.size > 10 * 1024 * 1024) {
      $("#importStatus").html( "<span style='color:red;'>The maximum file size is 10MB.</span>" );
      return;
    } else {
      $("#importStatus").html( "<span style='color:green;'> The file has been uploaded successfully.</span>" );
    }

    reader.onload = function() {
      $("#importContent").val( reader.result );
    };
    reader.onerror = function() {
      $("#importStatus").html( "<span style='color:red;'>Error: "+reader.error+".</span>" );
    };
    reader.readAsText(file);
  });

  $("#popupLabel").text("Import Graph");

  $('#popup').on('shown.bs.modal', function () {
    $("#importContent").focus();
  });
  $('#popup').modal("show");
}

function exportGraph(format){
  switch( format ) {
    case "graphml":
      globalCy.graphml({
        node: {
          css: false,
          data: true,
          position: false,
          discludeds: []
        },
        edge: {
          css: false,
          data: true,
          discludeds: []
        },
        layoutBy: "cola"
      });

      let graphml = globalCy.graphml();

      const blob = new Blob([graphml], {type: 'text/xml'});
      saveAs(blob, "arcade.graphml");
      break;

    case "json":
      var json = JSON.parse( JSON.stringify( globalResultset ) );
      json.records = [];
      json.settings = globalGraphSettings;
      var jsonBlob = new Blob([ JSON.stringify( json ) ], { type: 'application/javascript;charset=utf-8' });
      saveAs( jsonBlob, 'arcadedb-graph.json' );
      break;

    case "png":
      var imgBlob = globalCy.png( {"output": "blob"} );
      saveAs( imgBlob, 'arcadedb-graph.png' );
      break;

    case "jpeg":
      var imgBlob = globalCy.jpg( {"output": "blob"} );
      saveAs( imgBlob, 'arcadedb-graph.jpg' );
      break;
  }
}

function importSettings(){
  var html = "<center><h5>Copy below the JSON configuration to import or upload a file</h5>";
  html += "<input id='uploadFile' type='file' accept='.json, .txt, .js' />";
  html += "<center><textarea id='importContent' rows='30' cols='90'></textarea><br>";
  html += "<button id='importSettings' type='button' class='btn btn-primary'><i class='fa fa-upload'></i> Import settings</button><br>";
  html += "<div id='importStatus'><span>&nbsp;</span><div></center>";

  $("#popupBody").html(html);

  $("#importSettings").on( "click", function(){
    let imported = JSON.parse( $("#importContent").val() );
    globalGraphSettings = imported;
    renderGraph();
    $('#popup').modal("hide");
  });

  const reader = new FileReader();
  const fileUploader = document.getElementById('uploadFile');
  fileUploader.addEventListener('change', (event) => {
    const files = event.target.files;
    const file = files[0];
    console.log('files', files);

    if (file.size > 1024 * 1024) {
      $("#importStatus").html( "<span style='color:red;'>The maximum file size is 1MB.</span>" );
      return;
    } else {
      $("#importStatus").html( "<span style='color:green;'> The file has been uploaded successfully.</span>" );
    }

    reader.onload = function() {
      $("#importContent").val( reader.result );
    };
    reader.onerror = function() {
      $("#importStatus").html( "<span style='color:red;'>Error: "+reader.error+".</span>" );
    };
    reader.readAsText(file);
  });

  $("#popupLabel").text("Import Settings");

  $('#popup').on('shown.bs.modal', function () {
    $("#importContent").focus();
  });
  $('#popup').modal("show");
}

function exportSettings(){
  var html = "<center><h5>This is the JSON configuration exported</h5>";
  html += "<center><textarea id='exportContent' rows='30' cols='90' readonly></textarea><br>";
  html += "<button id='popupClipboard' type='button' data-clipboard-target='#exportContent' class='clipboard-trigger btn btn-primary'>";
  html += "<i class='fa fa-copy'></i> Copy to clipboard and close</button> or ";
  html += "<button id='downloadFile' type='button' class='btn btn-primary'>";
  html += "<i class='fa fa-download'></i> Download it</button></center>";

  $("#popupBody").html(html);

  $("#popupLabel").text("Export Settings");

  $("#exportContent").text( JSON.stringify( globalGraphSettings, null, 2 ) );

  new ClipboardJS("#popupClipboard")
    .on('success', function(e) {
      $('#popup').modal("hide")
    });;

  $("#downloadFile").on( "click", function(){
    var jsonBlob = new Blob([ $("#exportContent").val() ], { type: 'application/javascript;charset=utf-8' });
    saveAs( jsonBlob, 'arcadedb-studio-settings.json' );
    $('#popup').modal("hide");
  });

  $('#popup').on('shown.bs.modal', function () {
    $("#exportContent").focus();
  });
  $('#popup').modal();
}

function updateGraphStatus(warning){
  let html = "Displayed <b>" + Object.keys(globalRenderedVerticesRID).length + "</b> vertices and <b>"+globalTotalEdges+"</b> edges.";

  if( warning != null )
    html += " <b>WARNING</b>: " + warning;

  $("#graphStatus").html( html );
}
