import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import type { FC } from 'react';
import { AutoComplete, Input } from 'antd';
import {
  DeleteOutlined,
  HistoryOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';

import {
  deleteSearchHistoryApi,
  fetchSearchHistoryApi,
  fetchSuggestApi,
  triggerSuggestApi,
} from '@/service/search';
import type { ISearchHistoryItem } from '@/service/search';

import type { HeaderSearchConfig } from '../types';

interface HeaderSearchProps {
  search: HeaderSearchConfig;
  loggedIn: boolean;
  onSearch: (keyword: string) => void;
}

type SearchOption = {
  value: string;
  label: React.ReactNode;
  termId?: number;
  historyId?: number;
};

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
  const [options, setOptions] = useState<SearchOption[]>([]);
  const [history, setHistory] = useState<ISearchHistoryItem[]>([]);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [focused, setFocused] = useState(false);
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
    setHistoryLoaded(false);
    setFetching(false);
  }, [clearDebounce, routeKeyword]);

  useEffect(() => {
    if (loggedIn) return;
    setHistory([]);
    setHistoryLoaded(false);
  }, [loggedIn]);

  const toHistoryOptions = useCallback(
    (items: ISearchHistoryItem[]): SearchOption[] =>
      items.map((item) => ({
        value: item.keyword,
        historyId: item.id,
        label: (
          <span className="app-header__search-history-option">
            <HistoryOutlined />
            <span>{item.keyword}</span>
            <button
              type="button"
              className="app-header__search-history-delete"
              aria-label={`删除搜索历史 ${item.keyword}`}
              onMouseDown={(event) => event.preventDefault()}
              onClick={(event) => {
                event.stopPropagation();
                void deleteSearchHistoryApi(item.id).catch(() => undefined);
                setHistory((current) =>
                  current.filter((historyItem) => historyItem.id !== item.id),
                );
                setOptions((current) =>
                  current.filter((option) => option.historyId !== item.id),
                );
              }}
            >
              <DeleteOutlined />
            </button>
          </span>
        ),
      })),
    [],
  );

  const loadHistory = useCallback(() => {
    if (!loggedIn) {
      setOptions([]);
      return;
    }
    if (historyLoaded) {
      setOptions(toHistoryOptions(history));
      return;
    }
    void fetchSearchHistoryApi()
      .then((res) => {
        const next = res.code === 200 ? res.data || [] : [];
        setHistory(next);
        setOptions(toHistoryOptions(next));
        setHistoryLoaded(true);
      })
      .catch(() => {
        setHistoryLoaded(true);
        setOptions([]);
      });
  }, [history, historyLoaded, loggedIn, toHistoryOptions]);

  const loadSuggest = useCallback(
    (prefix: string) => {
      clearDebounce();
      const trimmed = prefix.trim();
      if (trimmed.length < MIN_SUGGEST_LEN) {
        setOptions(focused && loggedIn ? toHistoryOptions(history) : []);
        setFetching(false);
        return;
      }

      debounceRef.current = window.setTimeout(() => {
        const requestId = ++requestIdRef.current;
        setFetching(true);
        void fetchSuggestApi(trimmed)
          .then((res) => {
            if (requestId !== requestIdRef.current) return;
            setOptions(
              res.code === 200
                ? (res.data || []).map((item) => ({
                    value: item.term,
                    label: item.term,
                    termId: item.id,
                  }))
                : [],
            );
          })
          .catch(() => {
            if (requestId === requestIdRef.current) setOptions([]);
          })
          .finally(() => {
            if (requestId === requestIdRef.current) setFetching(false);
          });
      }, SUGGEST_DEBOUNCE_MS);
    },
    [clearDebounce, focused, history, loggedIn, toHistoryOptions],
  );

  const submit = useCallback(
    (value?: string) => {
      const next = (value ?? keyword).trim();
      if (!next) return;
      setOptions([]);
      onSearch(next);
    },
    [keyword, onSearch],
  );

  const handleSelect = useCallback(
    (value: string, option: { termId?: number }) => {
      const term = value.trim();
      if (!term) return;
      if (loggedIn) {
        void triggerSuggestApi({ termId: option.termId, term }).catch(
          () => undefined,
        );
      }
      setKeyword(term);
      submit(term);
    },
    [loggedIn, submit],
  );

  return (
    <div className="app-header__search">
      <AutoComplete
        className="app-header__search-autocomplete"
        value={keyword}
        options={options}
        open={focused && options.length > 0}
        onSelect={(value, option) =>
          handleSelect(String(value), option as { termId?: number })
        }
        onChange={(value) => {
          setKeyword(value);
          loadSuggest(value);
        }}
        onFocus={() => {
          setFocused(true);
          if (!keyword.trim()) loadHistory();
        }}
        onBlur={() => setFocused(false)}
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
