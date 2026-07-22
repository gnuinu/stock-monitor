package com.stockmonitor.market.kis;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin HTTP client for the KIS quotation endpoints used by this app. Each call
 * carries the app key/secret, a fresh bearer token and the endpoint-specific
 * {@code tr_id}. Responses are returned as {@link JsonNode} for tolerant
 * parsing; callers check {@code rt_cd == "0"} and read the relevant output node.
 *
 * <p>API references (KIS 개발자센터):
 * <ul>
 *   <li>국내주식 현재가 시세 — inquire-price / FHKST01010100</li>
 *   <li>국내주식 기간별 시세(일) — inquire-daily-itemchartprice / FHKST03010100</li>
 *   <li>해외주식 현재가 — quotations/price / HHDFS00000300</li>
 *   <li>해외주식 기간별시세 — quotations/dailyprice / HHDFS76240000</li>
 * </ul>
 */
public class KisClient {

    private final KisProperties props;
    private final KisTokenManager tokens;
    private final RestClient http;

    public KisClient(KisProperties props, KisTokenManager tokens) {
        this.props = props;
        this.tokens = tokens;
        this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    public JsonNode domesticPrice(String code) {
        String uri = UriComponentsBuilder.fromPath("/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", code)
                .toUriString();
        return get(uri, "FHKST01010100");
    }

    public JsonNode domesticDaily(String code, String fromYmd, String toYmd) {
        String uri = UriComponentsBuilder.fromPath("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", code)
                .queryParam("fid_input_date_1", fromYmd)
                .queryParam("fid_input_date_2", toYmd)
                .queryParam("fid_period_div_code", "D")
                .queryParam("fid_org_adj_prc", "0")
                .toUriString();
        return get(uri, "FHKST03010100");
    }

    public JsonNode overseasPrice(String excd, String symb) {
        String uri = UriComponentsBuilder.fromPath("/uapi/overseas-price/v1/quotations/price")
                .queryParam("AUTH", "")
                .queryParam("EXCD", excd)
                .queryParam("SYMB", symb)
                .toUriString();
        return get(uri, "HHDFS00000300");
    }

    public JsonNode overseasDaily(String excd, String symb, String toYmd) {
        String uri = UriComponentsBuilder.fromPath("/uapi/overseas-price/v1/quotations/dailyprice")
                .queryParam("AUTH", "")
                .queryParam("EXCD", excd)
                .queryParam("SYMB", symb)
                .queryParam("GUBN", "0")   // 0=daily
                .queryParam("BYMD", toYmd) // end date; empty = most recent
                .queryParam("MODP", "1")   // 1=adjusted price
                .toUriString();
        return get(uri, "HHDFS76240000");
    }

    private JsonNode get(String uri, String trId) {
        JsonNode body = http.get()
                .uri(uri)
                .headers(h -> {
                    h.setBearerAuth(tokens.bearer());
                    h.set("appkey", props.getAppKey());
                    h.set("appsecret", props.getAppSecret());
                    h.set("tr_id", trId);
                    h.set("custtype", "P");
                })
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new IllegalStateException("Empty KIS response for tr_id=" + trId);
        }
        String rtCd = body.path("rt_cd").asText("");
        if (!rtCd.isEmpty() && !"0".equals(rtCd)) {
            throw new IllegalStateException("KIS error tr_id=" + trId
                    + " rt_cd=" + rtCd + " msg=" + body.path("msg1").asText(""));
        }
        return body;
    }
}
