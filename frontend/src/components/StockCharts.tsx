import { useEffect, useRef } from 'react';
import {
  ColorType,
  createChart,
  CrosshairMode,
  IChartApi,
  LineStyle,
  LogicalRange,
  UTCTimestamp,
} from 'lightweight-charts';
import { CandleDto, Indicators, Point } from '../api';

const UP = '#e66767';
const DOWN = '#3987e5';
const COLORS = {
  sma5: '#c98500',
  sma20: '#199e70',
  sma60: '#d55181',
  sma120: '#9085e9',
  bollinger: 'rgba(137, 135, 129, 0.55)',
  rsi: '#9085e9',
  macd: '#c98500',
  signal: '#199e70',
};

const BASE_OPTIONS = {
  layout: {
    background: { type: ColorType.Solid, color: '#1a1a19' },
    textColor: '#898781',
    fontSize: 11,
  },
  grid: {
    vertLines: { color: '#2c2c2a' },
    horzLines: { color: '#2c2c2a' },
  },
  crosshair: { mode: CrosshairMode.Normal },
  rightPriceScale: { borderColor: '#383835' },
  timeScale: { borderColor: '#383835' },
} as const;

function toLine(points: Point[]) {
  return points.map((p) => ({ time: p.time as unknown as UTCTimestamp, value: p.value }));
}

interface Props {
  candles: CandleDto[];
  indicators: Indicators;
}

/**
 * Candlestick chart with MA/Bollinger overlays plus synchronized RSI and MACD
 * panes. Charts are rebuilt when data arrives; lightweight-charts handles
 * incremental interaction (crosshair, zoom, pan) natively.
 */
export default function StockCharts({ candles, indicators }: Props) {
  const mainRef = useRef<HTMLDivElement>(null);
  const rsiRef = useRef<HTMLDivElement>(null);
  const macdRef = useRef<HTMLDivElement>(null);
  // Preserve the visible range across data refreshes so polling doesn't reset zoom.
  const savedRange = useRef<LogicalRange | null>(null);
  const dataKey = useRef<string>('');

  useEffect(() => {
    if (!mainRef.current || !rsiRef.current || !macdRef.current || candles.length === 0) {
      return;
    }

    const main = createChart(mainRef.current, {
      ...BASE_OPTIONS,
      height: 380,
      autoSize: true,
    });
    const rsi = createChart(rsiRef.current, {
      ...BASE_OPTIONS,
      height: 130,
      autoSize: true,
      timeScale: { ...BASE_OPTIONS.timeScale, visible: false },
    });
    const macd = createChart(macdRef.current, {
      ...BASE_OPTIONS,
      height: 150,
      autoSize: true,
    });

    // ── main pane: candles + volume + overlays ──
    const candleSeries = main.addCandlestickSeries({
      upColor: UP,
      downColor: DOWN,
      borderUpColor: UP,
      borderDownColor: DOWN,
      wickUpColor: UP,
      wickDownColor: DOWN,
    });
    candleSeries.setData(
      candles.map((c) => ({
        time: c.time as unknown as UTCTimestamp,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close,
      })),
    );

    const volumeSeries = main.addHistogramSeries({
      priceScaleId: 'volume',
      priceFormat: { type: 'volume' },
      lastValueVisible: false,
      priceLineVisible: false,
    });
    main.priceScale('volume').applyOptions({
      scaleMargins: { top: 0.82, bottom: 0 },
      visible: false,
    });
    volumeSeries.setData(
      indicators.volume.map((v) => ({
        time: v.time as unknown as UTCTimestamp,
        value: v.value,
        color: v.up ? 'rgba(230, 103, 103, 0.4)' : 'rgba(57, 135, 229, 0.4)',
      })),
    );

    const lineDefaults = { lineWidth: 2, priceLineVisible: false, lastValueVisible: false } as const;
    for (const key of ['sma5', 'sma20', 'sma60', 'sma120'] as const) {
      const series = main.addLineSeries({ ...lineDefaults, color: COLORS[key] });
      series.setData(toLine(indicators[key]));
    }
    for (const band of ['upper', 'lower'] as const) {
      const series = main.addLineSeries({
        ...lineDefaults,
        lineWidth: 1,
        color: COLORS.bollinger,
        lineStyle: LineStyle.Dashed,
      });
      series.setData(toLine(indicators.bollinger[band]));
    }

    // ── RSI pane ──
    const rsiSeries = rsi.addLineSeries({ ...lineDefaults, color: COLORS.rsi });
    rsiSeries.setData(toLine(indicators.rsi));
    for (const level of [30, 70]) {
      rsiSeries.createPriceLine({
        price: level,
        color: '#383835',
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: '',
      });
    }

    // ── MACD pane ──
    const histSeries = macd.addHistogramSeries({
      lastValueVisible: false,
      priceLineVisible: false,
    });
    histSeries.setData(
      indicators.macd.histogram.map((p) => ({
        time: p.time as unknown as UTCTimestamp,
        value: p.value,
        color: p.value >= 0 ? 'rgba(230, 103, 103, 0.5)' : 'rgba(57, 135, 229, 0.5)',
      })),
    );
    const macdLine = macd.addLineSeries({ ...lineDefaults, color: COLORS.macd });
    macdLine.setData(toLine(indicators.macd.macd));
    const signalLine = macd.addLineSeries({ ...lineDefaults, color: COLORS.signal });
    signalLine.setData(toLine(indicators.macd.signal));

    // ── sync visible ranges across the three panes ──
    const charts: IChartApi[] = [main, rsi, macd];
    let syncing = false;
    const unsubscribers = charts.map((source) => {
      const handler = (range: LogicalRange | null) => {
        if (syncing || !range) return;
        syncing = true;
        savedRange.current = range;
        for (const target of charts) {
          if (target !== source) {
            target.timeScale().setVisibleLogicalRange(range);
          }
        }
        syncing = false;
      };
      source.timeScale().subscribeVisibleLogicalRangeChange(handler);
      return () => source.timeScale().unsubscribeVisibleLogicalRangeChange(handler);
    });

    const key = `${candles.length}:${candles[0].time}`;
    if (key === dataKey.current && savedRange.current) {
      main.timeScale().setVisibleLogicalRange(savedRange.current);
    } else {
      main.timeScale().fitContent();
    }
    dataKey.current = key;

    return () => {
      unsubscribers.forEach((u) => u());
      charts.forEach((c) => c.remove());
    };
  }, [candles, indicators]);

  return (
    <div className="chart-stack">
      <div className="legend-row" role="list" aria-label="차트 범례">
        <span className="key"><span className="swatch" style={{ background: COLORS.sma5 }} />MA5</span>
        <span className="key"><span className="swatch" style={{ background: COLORS.sma20 }} />MA20</span>
        <span className="key"><span className="swatch" style={{ background: COLORS.sma60 }} />MA60</span>
        <span className="key"><span className="swatch" style={{ background: COLORS.sma120 }} />MA120</span>
        <span className="key"><span className="swatch" style={{ background: COLORS.bollinger }} />볼린저밴드(20, 2σ)</span>
      </div>
      <div className="card chart-panel">
        <span className="panel-title">일봉 · 이동평균 · 볼린저밴드 · 거래량</span>
        <div ref={mainRef} />
      </div>
      <div className="card chart-panel">
        <span className="panel-title">RSI (14)</span>
        <div ref={rsiRef} />
      </div>
      <div className="card chart-panel">
        <span className="panel-title">MACD (12, 26, 9)</span>
        <div ref={macdRef} />
      </div>
    </div>
  );
}
