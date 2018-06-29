var table = $('#tblStatus').DataTable({
	"ajax" : 'api/statistics',
	"drawCallback" : function(settings) {
		var totalAck = totalUnAck = totalSuccessfulListener = totalFailedListener = 0;

		var api = this.api();

		api.data().each(function(col) {
			
			if (col[1]!=null) {
			  	col[1] ? totalSuccessfulListener+=1 : totalFailedListener += 1;
			}
			
			totalAck += col[2];
			totalUnAck += col[3];
		});

		console.log("setting to " + totalAck + ' and ' + totalUnAck)
		$('#agents').text(api.data().length);
		$('#ack').text(totalAck);
		$('#unack').text(totalUnAck);
		$('#listenerSuccess').text(totalSuccessfulListener);
		$('#listenerFailed').text(totalFailedListener);
	}
});

$(document).ready(function(){
	$.ajax({
		url : "api/statistics?actions", 
		statusCode: {
			200: function(response) {
				$("#pauseResumeButton").text(response.pauseResumeButtonText);
				
				$("#statsButton").text(response.statsButtonText);
			}
		}
	});
	
	$.ajax({
		url : "ofconfig", 
		statusCode: {
			200: function(setting) {
				$('#xmpp-host-input').val(setting.xmppHost)
				$('#xmpp-domain-input').val(setting.xmppDomain)
				$('#pu-name-input').val(setting.publishUser);
				$('#pu-pwd-input').val(setting.publishPassword);
				$('#pr-name-input').val(setting.presenceUser);
				$('#pr-pwd-input').val(setting.presencePassword);
				$('#publishrate').val(setting.publishRate);
				$('#total-consumers').val(setting.totalConsumers);
			   $("#listenerTypeBtn").text(setting.listenerType);
			}
		}
	});
	
	function _ajax_request(url, data, callback, method) {
	    return jQuery.ajax({
	        url: url,
	        type: method,
	        data: data,
	        success: callback
	    });
	}
	
	$.extend({
	    put: function(url, data, callback) {
	        return _ajax_request(url, data, callback, 'PUT');
	}});
	

	$('#unackListCollapse').on('show.bs.collapse', function () {
		$.ajax({
			url : "api/statistics?action=unackevents", 
			statusCode: {
				200: function(values) {
					var text="";
					var id;
					for (id in values) {
						text += "<span class='badge badge-primary font-weight-normal'>" + values[id] + "</span> ";
					}
					$('#unackListCollapse').html(text)
				}
			}
		});
	})
	
   $(".dropdown-menu a").click(function(e){
      $("#listenerTypeBtn").text($(this).text());
   });
	
		
});

$('#listenerTypeBtn');

$('#saveConfig').on('click', function(event) {
	event.preventDefault();
	var json = {};
	json.xmppHost = $('#xmpp-host-input').val();
	json.xmppDomain = $('#xmpp-domain-input').val();
	json.publishUser = $('#pu-name-input').val();
	json.publishPassword = $('#pu-pwd-input').val();
	json.presenceUser = $('#pr-name-input').val();
	json.presencePassword = $('#pr-pwd-input').val();
	json.listenerType = $("#listenerTypeBtn").text();
	json.publishRate = $('#publishrate').val();
	json.totalConsumers = $('#total-consumers').val();
	
	$.put('/ofconfig', JSON.stringify(json), function(result) {
		console.log("Got the response: " + result);
		$("#result").html(result);
	})
});



$('#reloadButton').on('click', function(event) {
	event.preventDefault();
	table.ajax.reload();

	/*
	 * console.log("ROW 0 is: " + table.row(0).data()) var newData = [1,
	 * 1001001,111,33]; //table.row(0).data(newData).draw();
	 * 
	 * table.data().each( function (d) { console.log("D is " + d) d[2] += 1; } );
	 * 
	 * table.draw();
	 */

});

$('#clearButton').on('click', function(event) {
	event.preventDefault();
	$.ajax({
		url : "api/statistics?action=clear",
		statusCode : {
			200 : function(response) {
				$("#result").html(response.responseText);
				table.ajax.reload();
			}
		}
	});
});

$('#pauseResumeButton').on('click', function(event) {
	event.preventDefault();
	var action = $("#pauseResumeButton").text().trim();

	$.ajax({
		url : "api/statistics?action=" + action, 
		statusCode: {
			200: function(response) {
				$("#result").html(response.responseText);
				var nextAction = (action==='Pause') ? 'Resume' : 'Pause';
				$("#pauseResumeButton").text(nextAction);
			}
		}
	});
});

$('#statsButton').on('click', function(event) {
	event.preventDefault();
	var action = $("#statsButton").text().trim();
	
	if (action==='Wait..') 
		return;
	
	$("#statsButton").text('Wait..');
	
	$.ajax({
		url : "api/statistics?action=" + action, 
		statusCode: {
			200: function(response) {
				$("#result").html(response.responseText);
				var nextAction = (action==='Record Stats') ? 'Stop Stats Recording' : 'Record Stats';
				$("#statsButton").text(nextAction);
			}
		}
	});
});
