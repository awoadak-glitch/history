#!/usr/bin/env python3
"""Reject host/module Firebase registration mixing in the compiled manifest."""
import argparse
import json
import pathlib
import xml.etree.ElementTree as E

A = '{http://schemas.android.com/apk/res/android}'
HOST = 'com.google.firebase.components.ComponentDiscoveryService'
MODULE = 'com.atheer.shell.SourceDiscoveryService'

def verify(manifest, host_baseline, module_baseline):
    app = E.parse(manifest).getroot().find('application')
    services = {n.get(A+'name'): n for n in app.findall('service')}
    for name, expected in [(HOST, host_baseline), (MODULE, module_baseline)]:
        if name not in services:
            raise ValueError('Missing isolated registration service: '+name)
        actual = {n.get(A+'name'): n.get(A+'value') for n in services[name]}
        if actual != expected:
            raise ValueError('Firebase registration changed or mixed: '+name)
        if services[name].get(A+'exported') != 'false':
            raise ValueError('Discovery metadata service must be private: '+name)
    return {'host_registrars_preserved': len(host_baseline),
            'module_registrars_isolated': len(module_baseline),
            'host_and_module_metadata_match_originals': True,
            'device_execution_tested': False}

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('manifest', type=pathlib.Path)
    p.add_argument('host_baseline', type=pathlib.Path)
    p.add_argument('module_baseline', type=pathlib.Path)
    a = p.parse_args()
    print(json.dumps(verify(a.manifest, json.loads(a.host_baseline.read_text()),
                           json.loads(a.module_baseline.read_text())), indent=2))
