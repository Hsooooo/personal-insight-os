import { useEffect, useState } from 'react';
import { ApiKeySection } from '@/components/mcp/ApiKeySection';
import { ApiKeyConnectGuide } from '@/components/mcp/ApiKeyConnectGuide';
import { NewKeyBanner } from '@/components/mcp/NewKeyBanner';
import { OAuthConnectGuide } from '@/components/mcp/OAuthConnectGuide';
import { OAuthGrantsSection } from '@/components/mcp/OAuthGrantsSection';
import { getMcpUrl } from '@/components/mcp/snippets';
import { useCopyToClipboard } from '@/components/mcp/useCopyToClipboard';
import type { ApiKey } from '@/types';

// 이전 버전이 localStorage에 보관하던 API 키 원문을 제거한다 (원문은 발급 직후 1회만 노출)
const LEGACY_LS_KEY_PREFIX = 'pios_mcp_key_';

function purgeLegacyStoredKeys() {
  try {
    Object.keys(localStorage)
      .filter((k) => k.startsWith(LEGACY_LS_KEY_PREFIX))
      .forEach((k) => localStorage.removeItem(k));
  } catch {
    // ignore
  }
}

export default function McpSettings() {
  const [newKey, setNewKey] = useState<ApiKey | null>(null);
  const [selectedKeyId, setSelectedKeyId] = useState<number | null>(null);
  const { copied, copy } = useCopyToClipboard();
  const mcpUrl = getMcpUrl();

  useEffect(() => {
    purgeLegacyStoredKeys();
  }, []);

  // 원문 키는 이번 세션에서 방금 발급한 키만 알 수 있다
  const selectedRawKey = newKey && newKey.id === selectedKeyId ? newKey.key : undefined;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">MCP 연동 설정</h2>
        <p className="text-muted-foreground">AI 에이전트와 Personal Insight OS를 연동하세요</p>
      </div>

      <OAuthConnectGuide mcpUrl={mcpUrl} copied={copied} onCopy={copy} />
      <OAuthGrantsSection />

      {newKey?.key && (
        <NewKeyBanner
          rawKey={newKey.key}
          copied={copied === 'newKey'}
          onCopy={() => {
            if (newKey.key) void copy(newKey.key, 'newKey');
          }}
          onClose={() => setNewKey(null)}
        />
      )}

      <ApiKeySection
        selectedKeyId={selectedKeyId}
        justIssuedKeyId={newKey?.id ?? null}
        onSelect={setSelectedKeyId}
        onCreated={(key) => {
          setNewKey(key);
          setSelectedKeyId(key.id);
        }}
        onDeleted={(id) => {
          if (selectedKeyId === id) setSelectedKeyId(null);
          if (newKey?.id === id) setNewKey(null);
        }}
      />

      <ApiKeyConnectGuide mcpUrl={mcpUrl} apiKey={selectedRawKey} copied={copied} onCopy={copy} />
    </div>
  );
}
