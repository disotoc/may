"""Tests for internal API endpoints (session-authenticated)."""
import pytest
from app import db as _db_ext
from app.models import User, Vehicle


class TestToggleDarkMode:
    def test_toggle_dark_mode_on(self, auth_client, test_user):
        initial = test_user.dark_mode
        resp = auth_client.post('/api/toggle-dark-mode')
        assert resp.status_code == 200
        data = resp.get_json()
        assert 'dark_mode' in data
        assert data['dark_mode'] == (not initial)

    def test_toggle_dark_mode_twice(self, auth_client, test_user):
        initial = test_user.dark_mode
        auth_client.post('/api/toggle-dark-mode')
        resp = auth_client.post('/api/toggle-dark-mode')
        assert resp.status_code == 200
        data = resp.get_json()
        assert data['dark_mode'] == initial

    def test_toggle_dark_mode_unauthenticated(self, client):
        resp = client.post('/api/toggle-dark-mode')
        assert resp.status_code in (302, 401)


class TestApiKeyManagement:
    def test_generate_api_key(self, auth_client, test_user):
        resp = auth_client.post('/api/key/generate')
        assert resp.status_code == 200
        data = resp.get_json()
        assert 'api_key' in data
        assert 'created_at' in data
        assert len(data['api_key']) > 10

    def test_generate_api_key_unauthenticated(self, client):
        resp = client.post('/api/key/generate')
        assert resp.status_code in (302, 401)

    def test_revoke_api_key(self, auth_client, test_user):
        # First generate a key
        auth_client.post('/api/key/generate')
        resp = auth_client.post('/api/key/revoke')
        assert resp.status_code == 200
        data = resp.get_json()
        assert data['success'] is True

    def test_revoke_api_key_unauthenticated(self, client):
        resp = client.post('/api/key/revoke')
        assert resp.status_code in (302, 401)


class TestVehicleStats:
    def test_get_vehicle_stats(self, auth_client, sample_vehicle):
        resp = auth_client.get(f'/api/vehicles/{sample_vehicle.id}/stats')
        assert resp.status_code == 200
        data = resp.get_json()
        assert 'consumption' in data
        assert 'expenses_by_category' in data
        assert 'total_fuel_cost' in data
        assert 'total_expense_cost' in data

    def test_get_vehicle_stats_unauthenticated(self, client, sample_vehicle):
        resp = client.get(f'/api/vehicles/{sample_vehicle.id}/stats')
        assert resp.status_code in (302, 401)

    def test_get_vehicle_stats_not_found(self, auth_client):
        resp = auth_client.get('/api/vehicles/99999/stats')
        assert resp.status_code == 404

    def test_get_vehicle_stats_other_users_vehicle(self, auth_client, admin_user, app):
        """Access to another user's vehicle should be denied."""
        other_vehicle = Vehicle(
            owner_id=admin_user.id,
            name='Admin Car',
            vehicle_type='car',
        )
        _db_ext.session.add(other_vehicle)
        _db_ext.session.commit()
        resp = auth_client.get(f'/api/vehicles/{other_vehicle.id}/stats')
        assert resp.status_code == 403


class TestLastOdometer:
    def test_get_last_odometer_no_logs(self, auth_client, sample_vehicle):
        resp = auth_client.get(f'/api/vehicles/{sample_vehicle.id}/last-odometer')
        assert resp.status_code == 200
        data = resp.get_json()
        assert 'odometer' in data

    def test_get_last_odometer_with_logs(self, auth_client, sample_vehicle, sample_fuel_log):
        resp = auth_client.get(f'/api/vehicles/{sample_vehicle.id}/last-odometer')
        assert resp.status_code == 200
        data = resp.get_json()
        assert data['odometer'] == sample_fuel_log.odometer

    def test_get_last_odometer_unauthenticated(self, client, sample_vehicle):
        resp = client.get(f'/api/vehicles/{sample_vehicle.id}/last-odometer')
        assert resp.status_code in (302, 401)

    def test_get_last_odometer_not_found(self, auth_client):
        resp = auth_client.get('/api/vehicles/99999/last-odometer')
        assert resp.status_code == 404


class TestProcessReminders:
    def test_process_reminders_as_admin(self, admin_client):
        resp = admin_client.post('/api/reminders/process')
        assert resp.status_code == 200
        data = resp.get_json()
        assert data['success'] is True
        assert 'stats' in data

    def test_process_reminders_with_api_key(self, client, test_user):
        api_key = test_user.generate_api_key()
        _db_ext.session.commit()
        resp = client.post(
            '/api/reminders/process',
            headers={'Authorization': f'Bearer {api_key}'}
        )
        assert resp.status_code == 200
        data = resp.get_json()
        assert data['success'] is True

    def test_process_reminders_with_internal_token(self, client, app):
        resp = client.post(
            '/api/reminders/process',
            headers={'X-Internal-Token': app.config['SECRET_KEY']}
        )
        assert resp.status_code == 200

    def test_process_reminders_unauthorized(self, client):
        resp = client.post('/api/reminders/process')
        assert resp.status_code == 401


class TestNtfyAuthentication:
    def test_send_ntfy_uses_basic_auth(self, monkeypatch):
        from app.services import notifications
        from app.services.notifications import NotificationService

        captured = {}

        class DummyResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

        def fake_urlopen(req, timeout=10):
            captured['headers'] = dict(req.header_items())
            captured['timeout'] = timeout
            return DummyResponse()

        monkeypatch.setattr(notifications, 'urlopen', fake_urlopen)

        success, error = NotificationService.send_ntfy(
            'https://ntfy.example.com/may',
            'Title',
            'Message',
            username='disocu',
            password='secret',
        )

        assert success is True
        assert error is None
        assert captured['headers']['Authorization'] == 'Basic ZGlzb2N1OnNlY3JldA=='

    def test_send_ntfy_uses_bearer_token(self, monkeypatch):
        from app.services import notifications
        from app.services.notifications import NotificationService

        captured = {}

        class DummyResponse:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

        def fake_urlopen(req, timeout=10):
            captured['headers'] = dict(req.header_items())
            return DummyResponse()

        monkeypatch.setattr(notifications, 'urlopen', fake_urlopen)

        success, error = NotificationService.send_ntfy(
            'https://ntfy.example.com/may',
            'Title',
            'Message',
            username='disocu',
            password='secret',
            token='tk_test',
        )

        assert success is True
        assert error is None
        assert captured['headers']['Authorization'] == 'Bearer tk_test'

    def test_test_notification_passes_ntfy_auth_values(self, auth_client, monkeypatch):
        from app.services.notifications import NotificationService

        captured = {}

        def fake_send_ntfy(topic, title, message, priority='default', username=None, password=None, token=None):
            captured.update({
                'topic': topic,
                'username': username,
                'password': password,
                'token': token,
            })
            return True, None

        monkeypatch.setattr(NotificationService, 'send_ntfy', fake_send_ntfy)

        resp = auth_client.post('/api/notifications/test', data={
            'notification_method': 'ntfy',
            'ntfy_topic': 'https://ntfy.example.com/may',
            'ntfy_username': 'disocu',
            'ntfy_password': 'secret',
            'ntfy_token': '',
        })

        assert resp.status_code == 200
        assert resp.get_json()['success'] is True
        assert captured == {
            'topic': 'https://ntfy.example.com/may',
            'username': 'disocu',
            'password': 'secret',
            'token': None,
        }
