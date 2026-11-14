"""IntrospectionTokenVerifier 단위 테스트."""

import httpx
import pytest
from conftest import API_KEY, READ_ONLY_TOKEN, READ_WRITE_TOKEN, RESOURCE, SECRET, FakeBackend

from pios_mcp.auth import IntrospectionTokenVerifier
from pios_mcp.backend import BackendUnavailable, PiosBackend


class Clock:
    def __init__(self, t: float = 1_700_000_000.0):
        self.t = t

    def __call__(self) -> float:
        return self.t


def make(settings, fake: FakeBackend, clock: Clock | None = None):
    backend = PiosBackend(settings, transport=httpx.MockTransport(fake.handler))
    return IntrospectionTokenVerifier(backend, settings, clock=clock or Clock())


@pytest.mark.anyio
async def test_active_oauth_token(settings, fake):
    v = make(settings, fake)
    tok = await v.verify_token(READ_WRITE_TOKEN)
    assert tok is not None
    assert tok.subject == "7"
    assert tok.client_id == "client-abc"
    assert tok.scopes == ["mcp:read", "mcp:write"]
    assert tok.resource == RESOURCE
    assert tok.expires_at == 4102444800
    req = fake.requests[0]
    assert req.headers["X-PIOS-Internal-Secret"] == SECRET
    assert req.headers["content-type"].startswith("application/x-www-form-urlencoded")


@pytest.mark.anyio
async def test_api_key_token(settings, fake):
    fake.tokens[API_KEY].pop("aud")  # aud 누락 시에도 api_key는 resource URL로 간주
    v = make(settings, fake)
    tok = await v.verify_token(API_KEY)
    assert tok is not None
    assert tok.resource == RESOURCE
    assert tok.client_id == "pios_api_key"
    assert tok.claims == {"token_type": "api_key"}


@pytest.mark.anyio
async def test_inactive_and_unknown(settings, fake):
    fake.tokens["dead"] = {"active": False}
    v = make(settings, fake)
    assert await v.verify_token("dead") is None
    assert await v.verify_token("unknown") is None
    assert await v.verify_token("") is None


@pytest.mark.anyio
async def test_active_without_sub_rejected(settings, fake):
    fake.tokens["nosub"] = {"active": True, "scope": "mcp:read", "aud": RESOURCE}
    assert await make(settings, fake).verify_token("nosub") is None


@pytest.mark.anyio
async def test_expired_token_rejected(settings, fake):
    clock = Clock()
    fake.tokens["old"] = {"active": True, "sub": "1", "scope": "mcp:read", "aud": RESOURCE, "exp": int(clock.t) - 1}
    assert await make(settings, fake, clock).verify_token("old") is None


@pytest.mark.anyio
async def test_aud_list_picks_resource(settings, fake):
    fake.tokens["multi"] = {"active": True, "sub": "1", "scope": "mcp:read", "aud": ["https://x", RESOURCE]}
    tok = await make(settings, fake).verify_token("multi")
    assert tok is not None and tok.resource == RESOURCE


@pytest.mark.anyio
async def test_positive_cache_at_most_60s(settings, fake):
    clock = Clock()
    v = make(settings, fake, clock)
    await v.verify_token(READ_ONLY_TOKEN)
    await v.verify_token(READ_ONLY_TOKEN)
    assert fake.introspect_calls == 1
    clock.t += 59
    await v.verify_token(READ_ONLY_TOKEN)
    assert fake.introspect_calls == 1
    clock.t += 2  # 61s 경과 → 재검증
    await v.verify_token(READ_ONLY_TOKEN)
    assert fake.introspect_calls == 2


@pytest.mark.anyio
async def test_cache_bounded_by_token_exp(settings, fake):
    clock = Clock()
    fake.tokens["short"] = {"active": True, "sub": "1", "scope": "mcp:read", "aud": RESOURCE, "exp": int(clock.t) + 10}
    v = make(settings, fake, clock)
    await v.verify_token("short")
    clock.t += 11
    assert await v.verify_token("short") is None  # 캐시가 exp를 넘겨 살아있지 않음
    assert fake.introspect_calls == 2


@pytest.mark.anyio
async def test_negative_cache_is_short(settings, fake):
    clock = Clock()
    v = make(settings, fake, clock)
    assert await v.verify_token("later-valid") is None
    fake.tokens["later-valid"] = {"active": True, "sub": "1", "scope": "mcp:read", "aud": RESOURCE}
    clock.t += settings.negative_cache_ttl + 0.1
    assert await v.verify_token("later-valid") is not None
    assert settings.negative_cache_ttl <= 10


@pytest.mark.anyio
async def test_revocation_visible_after_cache_ttl(settings, fake):
    clock = Clock()
    v = make(settings, fake, clock)
    assert await v.verify_token(READ_WRITE_TOKEN) is not None
    fake.tokens[READ_WRITE_TOKEN] = {"active": False}
    clock.t += 61
    assert await v.verify_token(READ_WRITE_TOKEN) is None


@pytest.mark.anyio
async def test_backend_down_raises_unavailable(settings, fake):
    fake.introspect_down = True
    with pytest.raises(BackendUnavailable):
        await make(settings, fake).verify_token(READ_WRITE_TOKEN)


@pytest.mark.anyio
async def test_wrong_internal_secret_treated_inactive(settings, fake):
    from dataclasses import replace

    s = replace(settings, internal_secret="wrong" * 10)
    assert await make(s, fake).verify_token(READ_WRITE_TOKEN) is None


@pytest.fixture
def anyio_backend():
    return "asyncio"
