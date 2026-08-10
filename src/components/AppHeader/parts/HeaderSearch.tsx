import React, {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { FC } from 'react';
import { AutoComplete, Input } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';

import { fetchSuggestApi, triggerSuggestApi } from '@/service/search';

import type { HeaderSearchConfig } from '../types';

interface HeaderSearchProps {
  search: HeaderSearchConfig;
  loggedIn: boolean;
  onSearch: (keyword: string) => void;
}

const SUGGEST_DEBOUNCE_MS = 280;
const MIN_SUGGEST_LEN = 1;

const HeaderSearch: FC<HeaderSearchProps> = ({
  search,
  loggedIn,
  onSearch,
}) => {
  const [searchParams] = useSearchParams();
  const routeKeyword = (searchParams.get('q') || '').trim();
  const [keyword, setKeyword] = useState('');
  const [options, setOptions] = useState<
    { value: string; label: string; termId?: number }[]
  >([]);
  const [fetching, setFetching] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const requestIdRef = useRef(0);

  const clearDebounce = useCallback(() => {
    if (debounceRef.current != null) {
      window.clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
  }, []);

  useEffect(() => () => clearDebounce(), [clearDebounce]);

  useEffect(() => {
    setKeyword(routeKeyword);
    requestIdRef.current += 1;
    clearDebounce();
    setOptions([]);
    setFetching(false);
  }, [clearDebounce, routeKeyword]);

  const loadSuggest = useCallback(
    (prefix: string) => {
      clearDebounce();
      const trimmed = prefix.trim();
      if (!loggedIn || trimmed.length < MIN_SUGGEST_LEN) {
        setOptions([]);
        setFetching(false);
        return;
      }

      debounceRef.current = window.setTimeout(() => {
        const requestId = ++requestIdRef.current;
        setFetching(true);
        void fetchSuggestApi(trimmed)
          .then((res) => {
            if (requestId !== requestIdRef.current) return;
            if (res.code !== 200) {
              setOptions([]);
              return;
            }
            setOptions(
              (res.data || []).map((item) => ({
                value: item.term,
                label: item.term,
                termId: item.id,
              })),
            );
          })
          .catch(() => {
            if (requestId !== requestIdRef.current) return;
            setOptions([]);
          })
          .finally(() => {
            if (requestId === requestIdRef.current) {
              setFetching(false);
            }
          });
      }, SUGGEST_DEBOUNCE_MS);
    },
    [clearDebounce, loggedIn],
  );

  const submit = useCallback(
    (value?: string) => {
      const next = (value ?? keyword).trim();
      if (!next) return;
      onSearch(next);
    },
    [keyword, onSearch],
  );

  const handleSelect = useCallback(
    (value: string, option: { termId?: number }) => {
      const term = value.trim();
      if (!term) return;
      void triggerSuggestApi({ termId: option.termId, term }).catch(
        () => undefined,
      );
      setKeyword(term);
      submit(term);
    },
    [submit],
  );

  const autoCompleteOptions = useMemo(
    () =>
      options.map((item) => ({
        value: item.value,
        label: item.label,
        termId: item.termId,
      })),
    [options],
  );

  return (
    <div className="app-header__search">
      <AutoComplete
        className="app-header__search-autocomplete"
        value={keyword}
        options={autoCompleteOptions}
        onSelect={(value, option) =>
          handleSelect(String(value), option as { termId?: number })
        }
        onSearch={loadSuggest}
        onChange={(value) => {
          setKeyword(value);
          loadSuggest(value);
        }}
        notFoundContent={fetching ? '加载中…' : undefined}
      >
        <Input
          allowClear
          onPressEnter={() => submit()}
          placeholder={search.placeholder}
          className="app-header__search-input"
          suffix={
            <button
              type="button"
              className="app-header__search-icon-btn"
              aria-label="搜索"
              onClick={() => submit()}
            >
              <SearchOutlined />
            </button>
          }
        />
      </AutoComplete>
    </div>
  );
};

export default memo(HeaderSearch);
