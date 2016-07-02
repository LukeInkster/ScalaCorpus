#
# Copyright 2015-2016 IBM Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys
import os
import json
import base64
import httplib
import argparse
import urllib
import subprocess
from wskitem import Item
from wskutil import addAuthenticatedCommand, request, getParams, getActivationArgument, getAnnotations, responseError, parseQName, getQName, apiBase, getPrettyJson

#
# 'wsk actions' CLI
#
class Action(Item):

    def __init__(self):
        super(Action, self).__init__('action', 'actions')

    def getItemSpecificCommands(self, parser, props):
        subcmd = parser.add_parser('create', help='create new action')
        subcmd.add_argument('--kind', help='the kind of the action runtime (example: swift:3)', type=str)
        subcmd.add_argument('name', help='the name of the action')
        subcmd.add_argument('artifact', help='artifact (e.g., file name) containing action definition')
        addAuthenticatedCommand(subcmd, props)
        subcmd.add_argument('--docker', help='treat artifact as docker image path on dockerhub', action='store_true')
        subcmd.add_argument('--copy', help='treat artifact as the name of an existing action', action='store_true')
        subcmd.add_argument('--sequence', help='treat artifact as comma separated sequence of actions to invoke', action='store_true')
        subcmd.add_argument('--lib', help='add library to artifact (must be a gzipped tar file)', type=argparse.FileType('r'))
        subcmd.add_argument('--shared', nargs='?', const='yes', choices=['yes', 'no'], help='shared action (default: private)')
        subcmd.add_argument('-a', '--annotation', help='annotations', nargs=2, action='append')
        subcmd.add_argument('-p', '--param', help='default parameters', nargs=2, action='append')
        subcmd.add_argument('-t', '--timeout', help='the timeout limit in milliseconds when the action will be terminated', type=int)
        subcmd.add_argument('-m', '--memory', help='the memory limit in MB of the container that runs the action', type=int)

        subcmd = parser.add_parser('update', help='update an existing action')
        subcmd.add_argument('--kind', help='the kind of the action runtime (example: swift:3)', type=str)
        subcmd.add_argument('name', help='the name of the action')
        subcmd.add_argument('artifact', nargs='?', default=None, help='artifact (e.g., file name) containing action definition')
        addAuthenticatedCommand(subcmd, props)
        subcmd.add_argument('--docker', help='treat artifact as docker image path on dockerhub', action='store_true')
        subcmd.add_argument('--copy', help='treat artifact as the name of an existing action', action='store_true')
        subcmd.add_argument('--sequence', help='treat artifact as comma separated sequence of actions to invoke', action='store_true')
        subcmd.add_argument('--lib', help='add library to artifact (must be a gzipped tar file)', type=argparse.FileType('r'))
        subcmd.add_argument('--shared', nargs='?', const='yes', choices=['yes', 'no'], help='shared action (default: private)')
        subcmd.add_argument('-a', '--annotation', help='annotations', nargs=2, action='append')
        subcmd.add_argument('-p', '--param', help='default parameters', nargs=2, action='append')
        subcmd.add_argument('-t', '--timeout', help='the timeout limit in milliseconds when the action will be terminated', type=int)
        subcmd.add_argument('-m', '--memory', help='the memory limit in MB of the container that runs the action', type=int)

        subcmd = parser.add_parser('invoke', help='invoke action')
        subcmd.add_argument('name', help='the name of the action to invoke')
        addAuthenticatedCommand(subcmd, props)
        subcmd.add_argument('-p', '--param', help='parameters', nargs=2, action='append')
        subcmd.add_argument('-b', '--blocking', action='store_true', help='blocking invoke')
        subcmd.add_argument('-r', '--result', help='show only activation result if a blocking activation (unless there is a failure)', action='store_true')

        self.addDefaultCommands(parser, props)

    def cmd(self, args, props):
        if args.subcmd == 'invoke':
            return self.invoke(args, props)
        else:
            return super(Action, self).cmd(args, props)

    def csvToQualifiedActions(self, props, csv):
        ns = props['namespace']
        actions = self.csvToList(csv)
        return [ getQName(a, ns) for a in actions ]

    def create(self, args, props, update):
        exe = self.getExec(args, props)
        validExe = exe is not None and 'kind' in exe
        fileok = args.artifact is None or os.path.isfile(args.artifact)
        if (update and fileok) or validExe: # if create action, then exe must be valid
            payload = {}
            if args.annotation:
                payload['annotations'] = getAnnotations(args)
            if args.param:
                payload['parameters'] = getParams(args)
            # API will accept limits == {} as limits not specified on an update
            if args.timeout or args.memory:
                payload['limits'] = self.getLimits(args)
            if validExe:
                payload['exec'] = exe
            if args.shared:
                self.addPublish(payload, args)
            return self.put(args, props, update, json.dumps(payload))
        else:
            if not args.copy:
                print >> sys.stderr, 'the artifact "%s" is not a valid file. If this is a docker image, use --docker.' % args.artifact
            else:
                print >> sys.stderr, 'the action "%s" does not exit, is malformed, or your are not entitled to it.' % args.artifact
            return 2

    def invoke(self, args, props):
        res = self.doInvoke(args, props)
        try:
            result = json.loads(res.read())
            if 'activationId' in result: # if args.result is true, there is no activation id
                print 'ok: invoked %(name)s with id %(id)s' % {'name': args.name, 'id': result['activationId'] }
            if res.status == httplib.OK: # true iff args.blocking is true
                print getPrettyJson(result) # prints the activation or just the result if args.result
                return 0
            elif res.status == httplib.ACCEPTED:
                return 0 if not args.blocking else res.status
            elif res.status == httplib.BAD_GATEWAY:
                return responseError(res, prefix = '', flatten = False)
            elif res.status == httplib.INTERNAL_SERVER_ERROR and 'code' not in result:
                return responseError(res, prefix = '', flatten = False)
            else:
                return responseError(res)
        except:
            return responseError(res)

    # invokes the action and returns HTTP response
    def doInvoke(self, args, props):
        namespace, pname = parseQName(args.name, props)
        url = 'https://%(apibase)s/namespaces/%(namespace)s/actions/%(name)s?blocking=%(blocking)s&result=%(result)s' % {
            'apibase': apiBase(props),
            'namespace': urllib.quote(namespace),
            'name': self.getSafeName(pname),
            'blocking': 'true' if args.blocking else 'false',
            'result': 'true' if 'result' in args and args.result else 'false'
        }
        payload = json.dumps(getActivationArgument(args))
        headers = {
            'Content-Type': 'application/json'
        }
        res = request('POST', url, payload, headers, auth=args.auth, verbose=args.verbose)
        return res

    # creates { timeout: msecs, memory: megabytes } action timeout/memory limits
    def getLimits(self, args):
        limits = {}
        if args.timeout:
            limits['timeout'] = args.timeout
        if args.memory :
            limits['memory'] = args.memory
        return limits

    # creates one of:
    # { kind: "nodejs", code: "js code", initializer: "base64 encoded string" } where initializer is optional
    # { kind: "nodejs6", code: "js6 code", initializer: "base64 encoded string" } where initializer is optional
    # { kind: "python", code: "python code" }
    # { kind: "swift", code: "swift code" }
    # { kind: "swift3", code: "swift3 code" }
    # { kind: "java", jar: "base64-encoded JAR", main: "FQN of main class" }
    # { kind: "blackbox", image: "docker image" }
    # { kind: "sequence", components: list of fully qualified actions }
    def getExec(self, args, props):
        exe = {}
        if args.docker:
            exe['kind'] = 'blackbox'
            exe['image'] = args.artifact
        elif args.copy:
            existingAction = args.artifact
            exe = self.getActionExec(args, props, existingAction)
        elif args.sequence:
            exe['kind'] = 'sequence'
            exe['components'] = self.csvToQualifiedActions(props, args.artifact)
        elif args.artifact is not None and os.path.isfile(args.artifact):
            contents = open(args.artifact, 'rb').read()
            if args.kind in ['swift:3','swift:3.0','swift:3.0.0']:
                exe['kind'] = 'swift:3'
                exe['code'] = contents
            elif args.artifact.endswith('.swift'):
                exe['kind'] = 'swift'
                exe['code'] = contents
            elif args.artifact.endswith('.py'):
                exe['kind'] = 'python'
                exe['code'] = contents
            elif args.artifact.endswith('.jar'):
                exe['kind'] = 'java'
                exe['jar'] = base64.b64encode(contents)
                exe['main'] = self.findMainClass(args.artifact)
            elif args.kind in ['nodejs']:
                exe['kind'] = 'nodejs'
                exe['code'] = contents
            elif args.kind in ['nodejs:6','nodejs:6.0','nodejs:6.0.0']:
                exe['kind'] = 'nodejs:6'
                exe['code'] = contents
            else:
                exe['kind'] = 'nodejs:default'
                exe['code'] = contents
        if args.lib:
            exe['initializer'] = base64.b64encode(args.lib.read())
        return exe

    def findMainClass(self, jarPath):
        signature = """public static com.google.gson.JsonObject main(com.google.gson.JsonObject);"""
        def run(cmd):
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            (o,e) = proc.communicate()
            if proc.returncode != 0:
                msg = "An error occurred while executing %s." % " ".join(cmd)
                if e.strip() != "":
                    msg = msg + "\n" + e
                raise Exception(msg)
            return o

        jarLines = run(["jar", "-tf", jarPath]).split("\n")
        classes = filter(lambda x: x.endswith(".class"), jarLines)
        classes = map(lambda x: x[:-6].replace("/", "."), classes)

        for c in classes:
            javapLines = run(["javap", "-cp", jarPath, c])
            if signature in javapLines:
                return c

        raise Exception("Couldn't find 'main' method in %s." % jarPath)


    def getActionExec(self, args, props, name):
        res = self.httpGet(args, props, name)
        if res.status == httplib.OK:
            execField = json.loads(res.read())['exec']
        else:
            execField = None
        return execField

    def csvToList(self, csv):
        return csv.split(',')
