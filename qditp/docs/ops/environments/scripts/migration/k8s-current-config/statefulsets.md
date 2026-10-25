# victoria-logs StatefulSet 关键配置

image: os-harbor-svc.default.svc.cloudos:443/itp/victoria-logs:v1.51.1
args: [u'-storageDataPath=/victoria-logs-data', u'-retentionPeriod=15d', u'-syslog.listenAddr.udp=:514']
resources: {u'requests': {u'cpu': u'1', u'memory': u'1Gi'}, u'limits': {u'cpu': u'2', u'memory': u'2Gi'}}
volumeClaimTemplates: [{u'status': {u'phase': u'Pending'}, u'kind': u'PersistentVolumeClaim', u'spec': {u'storageClassName': u'apigateway2', u'accessModes': [u'ReadWriteOnce'], u'resources': {u'requests': {u'storage': u'15Gi'}}, u'volumeMode': u'Filesystem'}, u'apiVersion': u'v1', u'metadata': {u'creationTimestamp': None, u'name': u'victoria-logs-victoria-logs-data'}}]
