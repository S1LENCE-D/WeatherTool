package com.simpleweather.app;

import java.util.ArrayList;
import java.util.List;

/** v9.51：级联搜索。候选第一项始终是「当前输入内容对应的行政区划实体」，
 *  随后列出其全部下一级区划（每页 5 条由调用方分页）：
 *   输入「浙江」→ 第一条「浙江省」+ 浙江省所有地级市
 *   输入「温州」→ 第一条「浙江省 · 温州市」+ 温州市所有区县
 *   输入「鹿城」→ 区县匹配「浙江省 · 温州市 · 鹿城区」（无下一级）
 *   输入「浙江温州」「深圳宝安」→ 省+市切分 / 市+区匹配混合候选
 *  全部候选统一显示「省 · 市 · 区」格式。 */
public final class PlaceSearch {

    private static final String[] MUNICIPALITIES = {"北京", "天津", "上海", "重庆"};

    /** 级联搜索入口：返回 List<String[]> {显示名, lat, lng} */
    public static List<String[]> search(String q) {
        List<String[]> out = new ArrayList<String[]>();
        String t = q == null ? "" : q.trim();
        if (t.isEmpty()) return out;

        // 1) 省级联：输入是省名（「浙江」「浙江省」）→ 第一条省 + 所有地级市
        String provKey = CityTable.provinceMatch(t);
        if (provKey != null && t.length() <= 4) {
            String provFull = CityTable.provinceFull(provKey);
            double[] pc = CityTable.provinceCoord(provKey);
            out.add(new String[]{provFull,
                    String.valueOf(pc[0]), String.valueOf(pc[1])});
            out.addAll(CityTable.listCitiesOf(provFull));
            // 直辖市下一级是区县：直接下钻
            boolean isMuni = false;
            for (String m : MUNICIPALITIES) {
                if (m.equals(provKey)) { isMuni = true; break; }
            }
            if (isMuni) {
                out.addAll(DistrictTable.listDistrictsOf(provFull));
            } else if (provKey.equals("海南")) {
                // 海南有省直辖县级行政区划（万宁、东方等），一并列出
                out.addAll(DistrictTable.listDistrictsOf("省直辖县级行政区划"));
            }
            return out;
        }

        // 2) 市级联：输入是纯市名（「温州」「温州市」）→ 第一条 省·市 + 所有区县
        String[] cm = CityTable.cityMatch(t);
        if (cm != null) {
            double[] cc = CityTable.cityCoord(cm[1]);
            if (cc != null) {
                String disp = cm[0].equals(cm[1]) ? cm[1] : cm[0] + " · " + cm[1];
                out.add(new String[]{disp, String.valueOf(cc[0]), String.valueOf(cc[1])});
            }
            out.addAll(DistrictTable.listDistrictsOf(cm[1]));
            return out;
        }

        // 3) 区县匹配（含 省+市+区 / 省+市 / 市+区 多段切分），显示省+市+区
        List<DistrictTable.Hit> ds = DistrictTable.lookup(t);
        for (DistrictTable.Hit h : ds) {
            out.add(new String[]{h.name, String.valueOf(h.lat), String.valueOf(h.lng)});
        }

        // 4) 市/省名模糊匹配（省+市切分场景如「浙江温州」→ 温州市；省名场景补省级候选）
        List<String[]> cs = CityTable.fuzzySearch(t);
        for (String[] c : cs) {
            String disp = c[0];
            String prov = CityTable.provinceOf(c[0]);
            if (prov != null) {
                disp = prov.equals(CityTable.cityFull(c[0]))
                        ? prov : prov + " · " + CityTable.cityFull(c[0]);
            } else {
                String f = CityTable.provinceFull(c[0]);
                if (f != null) disp = f;
            }
            out.add(new String[]{disp, c[1], c[2]});
        }
        return out;
    }
}
