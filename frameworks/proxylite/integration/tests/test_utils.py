import os
import time

import dcos
import inspect
import os
import shakedown
import subprocess


PACKAGE_NAME = 'proxylite'
WAIT_TIME_IN_SECONDS = 15 * 60

TASK_RUNNING_STATE = 'TASK_RUNNING'


# expected SECURITY values: 'permissive', 'strict', 'disabled'
if os.environ.get('SECURITY', '') == 'strict':
    print('Using strict mode test configuration')
    PRINCIPAL = 'service-acct'
    DEFAULT_OPTIONS_DICT = {
        "service": {
            "principal": PRINCIPAL,
            "secret_name": "secret"
        }
    }
else:
    print('Using default test configuration')
    PRINCIPAL = 'proxylite-principal'
    DEFAULT_OPTIONS_DICT = {}


def check_health():
    expected_tasks = 2

    def fn():
        try:
            return shakedown.get_service_tasks(PACKAGE_NAME)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            expected_tasks, len(running_tasks), len(tasks)))
        return (
            len(running_tasks) >= expected_tasks,
            'Service did not become healthy'
        )

    return spin(fn, success_predicate)


def get_deployment_plan():
    return _get_plan("deploy")


def _get_plan(plan):
    def fn():
        try:
            return dcos.http.get("{}/v1/plans/{}".format(shakedown.dcos_service_url(PACKAGE_NAME), plan))
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(response):
        print('Waiting for 200 response')
        success = False

        if hasattr(response, 'status_code'):
            success = response.status_code == 200

        return (
            success,
            'Failed to reach deployment endpoint'
        )

    return spin(fn, success_predicate)


def install(
        package_version=None,
        package_name=PACKAGE_NAME,
        additional_options = {},
        wait_for_completion=True):
    merged_options = _nested_dict_merge(DEFAULT_OPTIONS_DICT, additional_options)
    print('Installing {} with options: {}'.format(PACKAGE_NAME, merged_options))
    shakedown.install_package(
        package_name=PACKAGE_NAME,
        package_version=package_version,
        options_json=merged_options,
        wait_for_completion=wait_for_completion)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py '
        '-r proxylite-role -p proxylite-principal -z dcos-service-proxylite '
        '--auth_token={}'.format(
            shakedown.run_dcos_command(
                'config show core.dcos_acs_token'
            )[0].strip()
        )
    )


def spin(fn, success_predicate, *args, **kwargs):
    end_time = time.time() + WAIT_TIME_IN_SECONDS
    while time.time() < end_time:
        try:
            result = fn(*args, **kwargs)
        except Exception as e:
            is_successful, error_message = False, str(e)
        else:
            is_successful, error_message = success_predicate(result)

        if is_successful:
            print('Success state reached, exiting spin.')
            break
        print('Waiting for success state... err={}'.format(error_message))
        time.sleep(1)

    assert is_successful, error_message

    return result


def _nested_dict_merge(a, b, path=None):
    "ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge"
    if path is None: path = []
    a = a.copy()
    for key in b:
        if key in a:
            if isinstance(a[key], dict) and isinstance(b[key], dict):
                _nested_dict_merge(a[key], b[key], path + [str(key)])
            elif a[key] == b[key]:
                pass # same leaf value
            else:
                raise Exception('Conflict at %s' % '.'.join(path + [str(key)]))
        else:
            a[key] = b[key]
    return a


def request(request_fn, *args, **kwargs):
    def success_predicate(response):
        return (
            response.status_code == 200,
            'Request failed: %s' % response.content,
        )

    return spin(request_fn, success_predicate, *args, **kwargs)


def run_dcos_cli_cmd(cmd):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd)
    if ret != 0:
        err = "Got error code {} when running command 'dcos {}':\nstdout: {}\nstderr: {}".format(
            ret, cmd, stdout, stderr)
        print(err)
        raise Exception(err)
    return stdout
