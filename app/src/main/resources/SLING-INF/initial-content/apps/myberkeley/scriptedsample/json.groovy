response.setContentType("application/json");
response.setCharacterEncoding("UTF-8");

log.info "/apps/myberkeley/scriptedsample/json.groovy running against resource=${resource}, currentNode=${currentNode}"

innards = currentNode.properties.collect{ '{"' + it.name + '":"' + it.value.string + '"}' }.join(",")

println "[" + innards + ", {thisisfrom:groovy}]"
