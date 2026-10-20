#!/usr/bin/env python3
import yaml
from pathlib import Path

SRC = Path('/Users/tuanjie/workspace/company/chinasofti/qd/qditp/scripts/migration/ingress-config')
OUT = Path('/Users/tuanjie/workspace/company/chinasofti/qd/qditp/scripts/migration/ingress-config-cleaned')
OUT.mkdir(exist_ok=True)

def clean_metadata(obj):
    meta = obj.get('metadata', {})
    for key in ['creationTimestamp', 'resourceVersion', 'uid', 'generation', 'managedFields']:
        meta.pop(key, None)
    annotations = meta.get('annotations', {})
    for key in list(annotations.keys()):
        if key in ['kubectl.kubernetes.io/last-applied-configuration', 'deployment.kubernetes.io/revision']:
            del annotations[key]
    if not annotations:
        meta.pop('annotations', None)

def clean_deployment(doc):
    doc.pop('status', None)
    clean_metadata(doc)
    spec = doc.get('spec', {})
    for key in ['progressDeadlineSeconds', 'revisionHistoryLimit', 'dnsPolicy', 'schedulerName',
                'serviceAccount', 'serviceAccountName', 'terminationGracePeriodSeconds']:
        spec.pop(key, None)
    template = spec.get('template', {})
    template_meta = template.get('metadata', {})
    template_meta.pop('creationTimestamp', None)
    if not template_meta:
        template.pop('metadata', None)
    template_spec = template.get('spec', {})
    for key in ['dnsPolicy', 'schedulerName', 'serviceAccount', 'serviceAccountName',
                'terminationGracePeriodSeconds', 'terminationMessagePath', 'terminationMessagePolicy']:
        template_spec.pop(key, None)
    if 'affinity' in template_spec:
        aff = template_spec['affinity']
        if not aff or all(not v for v in aff.values()):
            del template_spec['affinity']
    for c in template_spec.get('containers', []):
        for key in ['terminationMessagePath', 'terminationMessagePolicy']:
            c.pop(key, None)

def clean_service(doc):
    doc.pop('status', None)
    clean_metadata(doc)
    spec = doc.get('spec', {})
    for key in ['clusterIP', 'clusterIPs', 'ipFamilies', 'ipFamilyPolicy', 'internalTrafficPolicy', 'sessionAffinity']:
        spec.pop(key, None)

def clean_istio(doc):
    doc.pop('status', None)
    clean_metadata(doc)

cleaners = {
    'Deployment': clean_deployment,
    'Service': clean_service,
    'Gateway': clean_istio,
    'VirtualService': clean_istio,
}

for f in sorted(SRC.glob('*.yaml')):
    docs = list(yaml.safe_load_all(f.read_text()))
    for doc in docs:
        if doc and 'kind' in doc:
            cleaners.get(doc['kind'], clean_istio)(doc)
    out_file = OUT / f.name
    out_file.write_text(yaml.dump_all(docs, default_flow_style=False, sort_keys=False))
    print(f'Cleaned: {out_file}')
