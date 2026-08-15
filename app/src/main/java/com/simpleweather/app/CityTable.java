package com.simpleweather.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * v9.25：国内城市/省中心坐标表（WGS-84，地级市中心）。
 * 供国内 IP 定位源（百度 qifu-api / useragentinfo 返回省市名）换算坐标——
 * 国外 IP 数据库对中国大陆 IP 归属记录残缺，常漂移到上海等枢纽城市，
 * 城市级坐标虽非精确但远优于漂移。
 * 数据来源：github.com/zhuyf8899/China_City_Geolocation_List（MIT）
 */
public final class CityTable {
    private static final HashMap<String, double[]> CITIES = new HashMap<String, double[]>();
    private static final HashMap<String, double[]> PROVINCES = new HashMap<String, double[]>();
    static {
        CITIES.put("七台河", new double[]{45.7800, 130.9500});
        CITIES.put("三亚", new double[]{18.2500, 109.5000});
        CITIES.put("三明", new double[]{26.2700, 117.6200});
        CITIES.put("三门峡", new double[]{34.7800, 111.2000});
        CITIES.put("上海", new double[]{31.2300, 121.4700});
        CITIES.put("上饶", new double[]{28.4300, 117.9200});
        CITIES.put("东莞", new double[]{23.0500, 113.7500});
        CITIES.put("东营", new double[]{37.4300, 118.6700});
        CITIES.put("中卫", new double[]{37.5200, 105.1800});
        CITIES.put("中山", new double[]{38.9200, 121.6300});
        CITIES.put("临夏", new double[]{35.5000, 103.0000});
        CITIES.put("临汾", new double[]{36.0800, 111.5200});
        CITIES.put("临沂", new double[]{35.0500, 118.3500});
        CITIES.put("临沧", new double[]{23.8800, 100.0800});
        CITIES.put("丹东", new double[]{40.1300, 124.3800});
        CITIES.put("丽水", new double[]{28.4500, 119.9200});
        CITIES.put("丽江", new double[]{26.8800, 100.2300});
        CITIES.put("乌兰察布", new double[]{40.9800, 113.1200});
        CITIES.put("乌海", new double[]{39.6700, 106.8200});
        CITIES.put("乌鲁木齐", new double[]{43.8000, 87.6000});
        CITIES.put("乐山", new double[]{29.5700, 103.7700});
        CITIES.put("九江", new double[]{29.6200, 115.8800});
        CITIES.put("云林", new double[]{23.7200, 120.5300});
        CITIES.put("云浮", new double[]{22.9200, 112.0300});
        CITIES.put("亳州", new double[]{33.8500, 115.7800});
        CITIES.put("伊春", new double[]{47.7300, 128.9000});
        CITIES.put("伊犁", new double[]{43.9200, 81.3200});
        CITIES.put("佛山", new double[]{23.0200, 113.1200});
        CITIES.put("佳木斯", new double[]{46.8200, 130.3700});
        CITIES.put("保定", new double[]{38.8700, 115.4700});
        CITIES.put("保山", new double[]{25.1200, 99.1700});
        CITIES.put("信阳", new double[]{32.1300, 114.0700});
        CITIES.put("克孜勒苏柯尔克孜", new double[]{39.7600, 76.2050});
        CITIES.put("克拉玛依", new double[]{45.6000, 84.8700});
        CITIES.put("六安", new double[]{31.7700, 116.5000});
        CITIES.put("六盘水", new double[]{26.6000, 104.8300});
        CITIES.put("兰州", new double[]{36.0700, 103.8200});
        CITIES.put("兴安", new double[]{50.4200, 124.1200});
        CITIES.put("内江", new double[]{29.5800, 105.0500});
        CITIES.put("凉山", new double[]{27.9000, 102.2700});
        CITIES.put("包头", new double[]{40.6500, 109.8300});
        CITIES.put("北京", new double[]{39.9000, 116.4000});
        CITIES.put("北海", new double[]{21.4800, 109.1200});
        CITIES.put("十堰", new double[]{32.6500, 110.7800});
        CITIES.put("南京", new double[]{32.0700, 118.7800});
        CITIES.put("南充", new double[]{30.7800, 106.0800});
        CITIES.put("南宁", new double[]{22.8200, 108.3700});
        CITIES.put("南平", new double[]{26.6500, 118.1700});
        CITIES.put("南投", new double[]{23.9200, 120.6700});
        CITIES.put("南昌", new double[]{28.6800, 115.8500});
        CITIES.put("南通", new double[]{31.9800, 120.8800});
        CITIES.put("博尔塔拉", new double[]{44.9000, 82.0700});
        CITIES.put("厦门", new double[]{24.4800, 118.0800});
        CITIES.put("双鸭山", new double[]{46.6300, 131.1500});
        CITIES.put("台东", new double[]{22.7500, 121.1500});
        CITIES.put("台中", new double[]{24.1500, 120.6700});
        CITIES.put("台北", new double[]{25.0200, 121.4700});
        CITIES.put("台南", new double[]{23.3200, 120.3200});
        CITIES.put("台州", new double[]{28.6800, 121.4300});
        CITIES.put("合肥", new double[]{31.8300, 117.2500});
        CITIES.put("吉安", new double[]{27.0500, 114.9000});
        CITIES.put("吉林市", new double[]{43.8300, 126.5500});
        CITIES.put("吐鲁番", new double[]{42.9500, 89.1700});
        CITIES.put("吕梁", new double[]{37.5200, 111.1300});
        CITIES.put("吴忠", new double[]{37.9800, 106.2000});
        CITIES.put("周口", new double[]{33.6200, 114.6500});
        CITIES.put("呼伦贝尔", new double[]{49.2200, 119.7700});
        CITIES.put("呼和浩特", new double[]{40.8300, 111.7300});
        CITIES.put("和田", new double[]{37.1200, 79.9200});
        CITIES.put("咸宁", new double[]{29.8500, 114.3200});
        CITIES.put("咸阳", new double[]{34.3300, 108.7000});
        CITIES.put("哈密", new double[]{42.8300, 93.5200});
        CITIES.put("哈尔滨", new double[]{45.8000, 126.5300});
        CITIES.put("唐山", new double[]{39.6300, 118.2000});
        CITIES.put("商丘", new double[]{34.4500, 115.6500});
        CITIES.put("商洛", new double[]{33.8700, 109.9300});
        CITIES.put("喀什", new double[]{39.4700, 75.9800});
        CITIES.put("嘉义", new double[]{23.4800, 120.4300});
        CITIES.put("嘉兴", new double[]{30.7500, 120.7500});
        CITIES.put("嘉峪关", new double[]{39.8000, 98.2700});
        CITIES.put("四平", new double[]{43.1700, 124.3500});
        CITIES.put("固原", new double[]{36.0000, 106.2800});
        CITIES.put("基隆", new double[]{25.1300, 121.7300});
        CITIES.put("塔城", new double[]{46.7500, 82.9800});
        CITIES.put("大兴安岭", new double[]{50.4200, 124.1200});
        CITIES.put("大同", new double[]{46.0300, 124.8200});
        CITIES.put("大庆", new double[]{46.5800, 125.0300});
        CITIES.put("大理", new double[]{25.6000, 100.2300});
        CITIES.put("大连", new double[]{38.9200, 121.6200});
        CITIES.put("天水", new double[]{34.5800, 105.7200});
        CITIES.put("天津", new double[]{39.1200, 117.2000});
        CITIES.put("太原", new double[]{37.8700, 112.5500});
        CITIES.put("威海", new double[]{37.5200, 122.1200});
        CITIES.put("娄底", new double[]{27.7300, 112.0000});
        CITIES.put("孝感", new double[]{30.9300, 113.9200});
        CITIES.put("宁德", new double[]{26.6700, 119.5200});
        CITIES.put("安庆", new double[]{30.5300, 117.0500});
        CITIES.put("安康", new double[]{32.6800, 109.0200});
        CITIES.put("安阳", new double[]{36.1000, 114.3800});
        CITIES.put("安顺", new double[]{26.2500, 105.9500});
        CITIES.put("定西", new double[]{35.5800, 104.6200});
        CITIES.put("宜兰", new double[]{24.7700, 121.7500});
        CITIES.put("宜宾", new double[]{28.7000, 104.5500});
        CITIES.put("宜昌", new double[]{30.7000, 111.2800});
        CITIES.put("宜春", new double[]{27.8000, 114.3800});
        CITIES.put("宝鸡", new double[]{34.3700, 107.1300});
        CITIES.put("宣城", new double[]{30.9500, 118.7500});
        CITIES.put("宿州", new double[]{33.6300, 116.9800});
        CITIES.put("宿迁", new double[]{33.9700, 118.2800});
        CITIES.put("屏东", new double[]{22.6700, 120.4800});
        CITIES.put("山南", new double[]{29.2300, 91.7700});
        CITIES.put("岳阳", new double[]{29.1500, 113.1200});
        CITIES.put("崇左", new double[]{22.4000, 107.3700});
        CITIES.put("巢湖", new double[]{31.6000, 117.8700});
        CITIES.put("巴中", new double[]{31.8500, 106.7700});
        CITIES.put("巴彦淖尔", new double[]{40.7500, 107.4200});
        CITIES.put("巴音郭楞", new double[]{41.7700, 86.1500});
        CITIES.put("常州", new double[]{31.7800, 119.9500});
        CITIES.put("常德", new double[]{29.0500, 111.6800});
        CITIES.put("平凉", new double[]{35.5500, 106.6700});
        CITIES.put("平顶山", new double[]{33.7700, 113.1800});
        CITIES.put("广元", new double[]{32.4300, 105.8300});
        CITIES.put("广安", new double[]{30.4700, 106.6300});
        CITIES.put("广州", new double[]{23.1300, 113.2700});
        CITIES.put("庆阳", new double[]{35.7300, 107.6300});
        CITIES.put("廊坊", new double[]{39.5200, 116.7000});
        CITIES.put("延安", new double[]{36.6000, 109.4800});
        CITIES.put("延边", new double[]{42.8800, 129.5000});
        CITIES.put("开封", new double[]{34.8000, 114.3000});
        CITIES.put("张家口", new double[]{40.8200, 114.8800});
        CITIES.put("张家界", new double[]{29.1300, 110.4700});
        CITIES.put("张掖", new double[]{38.9300, 100.4500});
        CITIES.put("彰化", new double[]{24.0800, 120.5300});
        CITIES.put("徐州", new double[]{34.2700, 117.1800});
        CITIES.put("德宏", new double[]{24.4300, 98.5800});
        CITIES.put("德州", new double[]{37.4500, 116.3000});
        CITIES.put("德阳", new double[]{31.1300, 104.3800});
        CITIES.put("忻州", new double[]{38.4200, 112.7300});
        CITIES.put("怀化", new double[]{27.5700, 110.0000});
        CITIES.put("怒江", new double[]{25.8500, 98.8500});
        CITIES.put("恩施", new double[]{30.3000, 109.4700});
        CITIES.put("惠州", new double[]{23.1200, 114.4200});
        CITIES.put("成都", new double[]{30.6700, 104.0700});
        CITIES.put("扬州", new double[]{32.4000, 119.4000});
        CITIES.put("承德", new double[]{40.9700, 117.9300});
        CITIES.put("抚州", new double[]{28.0000, 116.3500});
        CITIES.put("抚顺", new double[]{41.8800, 123.9000});
        CITIES.put("拉萨", new double[]{29.6500, 91.1300});
        CITIES.put("揭阳", new double[]{23.5500, 116.3700});
        CITIES.put("攀枝花", new double[]{26.5800, 101.7200});
        CITIES.put("文山", new double[]{23.3700, 104.2500});
        CITIES.put("新乡", new double[]{35.2000, 113.8000});
        CITIES.put("新余", new double[]{27.8200, 114.9200});
        CITIES.put("新北", new double[]{31.8300, 119.9700});
        CITIES.put("新竹", new double[]{24.8200, 120.9500});
        CITIES.put("无锡", new double[]{31.5700, 120.3000});
        CITIES.put("日喀则", new double[]{29.2700, 88.8800});
        CITIES.put("日照", new double[]{35.4200, 119.5200});
        CITIES.put("昆明", new double[]{25.0500, 102.7200});
        CITIES.put("昌吉", new double[]{44.0200, 87.3000});
        CITIES.put("昌都", new double[]{31.1300, 97.1800});
        CITIES.put("昭通", new double[]{27.3300, 103.7200});
        CITIES.put("晋中", new double[]{37.6800, 112.7500});
        CITIES.put("晋城", new double[]{35.5000, 112.8300});
        CITIES.put("普洱", new double[]{23.4300, 100.7350});
        CITIES.put("景德镇", new double[]{29.2700, 117.1700});
        CITIES.put("曲靖", new double[]{25.5000, 103.8000});
        CITIES.put("朔州", new double[]{39.3300, 112.4300});
        CITIES.put("朝阳", new double[]{41.5800, 120.4700});
        CITIES.put("本溪", new double[]{41.3000, 123.7700});
        CITIES.put("来宾", new double[]{23.7300, 109.2300});
        CITIES.put("杭州", new double[]{30.2800, 120.1500});
        CITIES.put("松原", new double[]{45.1300, 124.8200});
        CITIES.put("林芝", new double[]{29.6800, 94.3700});
        CITIES.put("果洛", new double[]{34.4800, 100.2300});
        CITIES.put("枣庄", new double[]{34.8200, 117.3200});
        CITIES.put("柳州", new double[]{24.3300, 109.4200});
        CITIES.put("株洲", new double[]{27.7200, 113.1300});
        CITIES.put("桂林", new double[]{25.2800, 110.2800});
        CITIES.put("桃园", new double[]{24.9700, 121.3000});
        CITIES.put("梅州", new double[]{24.2800, 116.1200});
        CITIES.put("梧州", new double[]{23.4800, 111.2700});
        CITIES.put("楚雄", new double[]{25.0300, 101.5500});
        CITIES.put("榆林", new double[]{38.2800, 109.7300});
        CITIES.put("武威", new double[]{37.9300, 102.6300});
        CITIES.put("武汉", new double[]{30.6000, 114.3000});
        CITIES.put("毕节", new double[]{27.3000, 105.2800});
        CITIES.put("永州", new double[]{26.4300, 111.6200});
        CITIES.put("汉中", new double[]{33.0700, 107.0200});
        CITIES.put("汕头", new double[]{23.3500, 116.6800});
        CITIES.put("汕尾", new double[]{22.7800, 115.3700});
        CITIES.put("江门", new double[]{22.5800, 113.0800});
        CITIES.put("池州", new double[]{30.6700, 117.4800});
        CITIES.put("沈阳", new double[]{41.8000, 123.4300});
        CITIES.put("沧州", new double[]{38.3000, 116.8300});
        CITIES.put("河池", new double[]{24.7000, 108.0700});
        CITIES.put("河源", new double[]{23.7300, 114.7000});
        CITIES.put("泉州", new double[]{24.8800, 118.6700});
        CITIES.put("泰安", new double[]{36.2000, 117.0800});
        CITIES.put("泰州", new double[]{32.4500, 119.9200});
        CITIES.put("泸州", new double[]{28.8700, 105.4300});
        CITIES.put("洛阳", new double[]{34.6200, 112.4500});
        CITIES.put("济南", new double[]{36.6700, 116.9800});
        CITIES.put("济宁", new double[]{35.4200, 116.5800});
        CITIES.put("海东", new double[]{36.5000, 102.1200});
        CITIES.put("海北", new double[]{36.9700, 100.9000});
        CITIES.put("海南自治州", new double[]{35.8950, 102.3750});
        CITIES.put("海口", new double[]{20.0300, 110.3200});
        CITIES.put("海西", new double[]{37.3700, 97.3700});
        CITIES.put("淄博", new double[]{36.8200, 118.0500});
        CITIES.put("淮北", new double[]{33.9500, 116.8000});
        CITIES.put("淮南", new double[]{32.6300, 117.0000});
        CITIES.put("淮安", new double[]{33.6200, 119.0200});
        CITIES.put("深圳", new double[]{22.5500, 114.0500});
        CITIES.put("清远", new double[]{23.7000, 113.0300});
        CITIES.put("温州", new double[]{28.0000, 120.7000});
        CITIES.put("渭南", new double[]{34.5000, 109.5000});
        CITIES.put("湖州", new double[]{30.9000, 120.0800});
        CITIES.put("湘潭", new double[]{27.7800, 112.9500});
        CITIES.put("湘西", new double[]{28.3200, 109.7300});
        CITIES.put("湛江", new double[]{21.2700, 110.3500});
        CITIES.put("滁州", new double[]{32.3000, 118.3200});
        CITIES.put("滨州", new double[]{37.3800, 117.9700});
        CITIES.put("漯河", new double[]{33.5800, 114.0200});
        CITIES.put("漳州", new double[]{24.5200, 117.6500});
        CITIES.put("潍坊", new double[]{36.7000, 119.1500});
        CITIES.put("潮州", new double[]{23.6700, 116.6200});
        CITIES.put("澎湖", new double[]{23.5800, 119.5800});
        CITIES.put("澳门", new double[]{22.1300, 113.3300});
        CITIES.put("濮阳", new double[]{35.7700, 115.0300});
        CITIES.put("烟台", new double[]{37.4500, 121.4300});
        CITIES.put("焦作", new double[]{35.2200, 113.2500});
        CITIES.put("牡丹江", new double[]{44.5800, 129.6000});
        CITIES.put("玉林", new double[]{22.6300, 110.1700});
        CITIES.put("玉树", new double[]{33.0000, 97.0200});
        CITIES.put("玉溪", new double[]{24.3500, 102.5500});
        CITIES.put("珠海", new double[]{22.2700, 113.5700});
        CITIES.put("甘南", new double[]{47.9200, 123.5000});
        CITIES.put("甘孜", new double[]{30.0500, 101.9700});
        CITIES.put("白城", new double[]{45.6200, 122.8300});
        CITIES.put("白山", new double[]{41.9300, 126.4200});
        CITIES.put("白银", new double[]{36.5500, 104.1800});
        CITIES.put("百色", new double[]{23.9000, 106.6200});
        CITIES.put("益阳", new double[]{28.6000, 112.3200});
        CITIES.put("盐城", new double[]{33.3500, 120.1500});
        CITIES.put("盘锦", new double[]{41.1200, 122.0700});
        CITIES.put("眉山", new double[]{30.0500, 103.8300});
        CITIES.put("石嘴山", new double[]{39.0200, 106.3800});
        CITIES.put("石家庄", new double[]{38.0500, 114.5200});
        CITIES.put("福州", new double[]{26.0800, 119.3000});
        CITIES.put("秦皇岛", new double[]{39.9300, 119.6000});
        CITIES.put("红河", new double[]{23.3700, 103.4000});
        CITIES.put("绍兴", new double[]{30.0800, 120.4700});
        CITIES.put("绥化", new double[]{46.6300, 126.9800});
        CITIES.put("绵阳", new double[]{31.4700, 104.7300});
        CITIES.put("聊城", new double[]{36.4500, 115.9800});
        CITIES.put("肇庆", new double[]{23.0500, 112.4700});
        CITIES.put("自贡", new double[]{29.3500, 104.7800});
        CITIES.put("舟山", new double[]{30.0000, 122.2000});
        CITIES.put("芜湖", new double[]{31.1500, 118.5700});
        CITIES.put("花莲", new double[]{23.9800, 121.6000});
        CITIES.put("苏州", new double[]{31.3000, 120.5800});
        CITIES.put("苗栗", new double[]{24.5300, 120.8000});
        CITIES.put("茂名", new double[]{21.6700, 110.9200});
        CITIES.put("荆州", new double[]{30.3300, 112.2300});
        CITIES.put("荆门", new double[]{31.0300, 112.2000});
        CITIES.put("莆田", new double[]{25.4300, 119.0000});
        CITIES.put("莱芜", new double[]{36.2200, 117.6700});
        CITIES.put("菏泽", new double[]{35.2600, 115.5850});
        CITIES.put("萍乡", new double[]{27.6300, 113.8500});
        CITIES.put("营口", new double[]{40.6700, 122.2300});
        CITIES.put("葫芦岛", new double[]{40.7200, 120.8300});
        CITIES.put("蚌埠", new double[]{32.9200, 117.3800});
        CITIES.put("衡水", new double[]{37.7300, 115.6800});
        CITIES.put("衡阳", new double[]{26.9000, 112.5700});
        CITIES.put("衢州", new double[]{28.9300, 118.8700});
        CITIES.put("襄樊", new double[]{32.0200, 112.1500});
        CITIES.put("西双版纳", new double[]{22.0200, 100.8000});
        CITIES.put("西宁", new double[]{36.6200, 101.7800});
        CITIES.put("西安", new double[]{34.2700, 108.9300});
        CITIES.put("许昌", new double[]{34.0000, 113.8300});
        CITIES.put("贵港", new double[]{23.1000, 109.6000});
        CITIES.put("贵阳", new double[]{26.6500, 106.6300});
        CITIES.put("贺州", new double[]{24.4200, 111.5500});
        CITIES.put("资阳", new double[]{28.6000, 112.3200});
        CITIES.put("赣州", new double[]{25.8300, 114.9300});
        CITIES.put("赤峰", new double[]{42.2700, 118.9200});
        CITIES.put("辽源", new double[]{42.8800, 125.1300});
        CITIES.put("辽阳", new double[]{41.2200, 123.0700});
        CITIES.put("达州", new double[]{31.2200, 107.5000});
        CITIES.put("运城", new double[]{35.0200, 110.9800});
        CITIES.put("连云港", new double[]{34.6000, 119.2200});
        CITIES.put("连江", new double[]{26.2000, 119.5300});
        CITIES.put("迪庆", new double[]{27.8300, 99.7000});
        CITIES.put("通化", new double[]{41.7300, 125.9300});
        CITIES.put("通辽", new double[]{43.6200, 122.2700});
        CITIES.put("遂宁", new double[]{30.5200, 105.5700});
        CITIES.put("遵义", new double[]{27.7300, 106.9200});
        CITIES.put("邢台", new double[]{37.0700, 114.4800});
        CITIES.put("那曲", new double[]{31.4800, 92.0700});
        CITIES.put("邯郸", new double[]{36.6200, 114.4800});
        CITIES.put("邵阳", new double[]{27.2500, 111.4700});
        CITIES.put("郑州", new double[]{34.7500, 113.6200});
        CITIES.put("郴州", new double[]{25.7800, 113.0200});
        CITIES.put("鄂尔多斯", new double[]{39.6200, 109.8000});
        CITIES.put("鄂州", new double[]{30.4000, 114.8800});
        CITIES.put("酒泉", new double[]{39.7500, 98.5200});
        CITIES.put("重庆", new double[]{29.5700, 106.5500});
        CITIES.put("金华", new double[]{29.0800, 119.6500});
        CITIES.put("金昌", new double[]{38.5000, 102.1800});
        CITIES.put("金门", new double[]{24.4300, 118.3200});
        CITIES.put("钦州", new double[]{21.9500, 108.6200});
        CITIES.put("铁岭", new double[]{42.3000, 123.8300});
        CITIES.put("铜川", new double[]{34.9000, 108.9300});
        CITIES.put("铜陵", new double[]{30.9500, 117.7800});
        CITIES.put("银川", new double[]{38.4700, 106.2800});
        CITIES.put("锡林郭勒", new double[]{43.9500, 116.0700});
        CITIES.put("锦州", new double[]{41.1000, 121.1300});
        CITIES.put("镇江", new double[]{32.2000, 119.4500});
        CITIES.put("长春", new double[]{43.9000, 125.3200});
        CITIES.put("长沙", new double[]{28.2300, 112.9300});
        CITIES.put("长治", new double[]{36.0500, 113.0300});
        CITIES.put("阜新", new double[]{42.0700, 121.7500});
        CITIES.put("阜阳", new double[]{32.9000, 115.8200});
        CITIES.put("防城港", new double[]{21.7000, 108.3500});
        CITIES.put("阳江", new double[]{21.8700, 111.9800});
        CITIES.put("阳泉", new double[]{37.8500, 113.5700});
        CITIES.put("阿克苏", new double[]{41.1700, 80.2700});
        CITIES.put("阿勒泰", new double[]{47.8500, 88.1300});
        CITIES.put("阿坝", new double[]{32.9000, 101.7000});
        CITIES.put("阿拉善盟", new double[]{38.8300, 105.6700});
        CITIES.put("阿里", new double[]{32.5000, 80.1000});
        CITIES.put("陇南", new double[]{33.4000, 104.9200});
        CITIES.put("随州", new double[]{31.7200, 113.3700});
        CITIES.put("雅安", new double[]{29.9800, 103.0000});
        CITIES.put("青岛", new double[]{36.0700, 120.3800});
        CITIES.put("鞍山", new double[]{41.1000, 122.9800});
        CITIES.put("韶关", new double[]{24.8200, 113.6000});
        CITIES.put("香港", new double[]{22.2000, 114.0800});
        CITIES.put("马鞍山", new double[]{31.7000, 118.5000});
        CITIES.put("驻马店", new double[]{32.9800, 114.0200});
        CITIES.put("高雄", new double[]{22.6300, 120.3700});
        CITIES.put("鸡西", new double[]{45.3000, 130.9700});
        CITIES.put("鹤壁", new double[]{35.7500, 114.2800});
        CITIES.put("鹤岗", new double[]{47.3300, 130.2700});
        CITIES.put("鹰潭", new double[]{28.2700, 117.0700});
        CITIES.put("黄冈", new double[]{30.4500, 114.8700});
        CITIES.put("黄南", new double[]{35.5200, 102.0200});
        CITIES.put("黄山", new double[]{29.7200, 118.3300});
        CITIES.put("黄石", new double[]{30.2000, 115.0300});
        CITIES.put("黑河", new double[]{50.2500, 127.4800});
        CITIES.put("黔东南", new double[]{26.5800, 107.9700});
        CITIES.put("黔南", new double[]{26.2700, 107.5200});
        CITIES.put("黔西南", new double[]{25.4050, 105.5550});
        CITIES.put("齐齐哈尔", new double[]{47.3300, 123.9500});
        CITIES.put("龙岩", new double[]{25.1000, 117.0300});

        PROVINCES.put("上海", new double[]{31.2304, 121.4737});
        PROVINCES.put("云南", new double[]{25.0389, 102.7183});
        PROVINCES.put("内蒙古", new double[]{40.8426, 111.7492});
        PROVINCES.put("北京", new double[]{39.9042, 116.4074});
        PROVINCES.put("台湾", new double[]{25.0330, 121.5654});
        PROVINCES.put("吉林", new double[]{43.8171, 125.3235});
        PROVINCES.put("四川", new double[]{30.5730, 104.0668});
        PROVINCES.put("天津", new double[]{39.1252, 117.1906});
        PROVINCES.put("宁夏", new double[]{38.4872, 106.2309});
        PROVINCES.put("安徽", new double[]{31.8210, 117.2272});
        PROVINCES.put("山东", new double[]{36.6512, 117.1201});
        PROVINCES.put("山西", new double[]{37.8706, 112.5489});
        PROVINCES.put("广东", new double[]{23.1291, 113.2644});
        PROVINCES.put("广西", new double[]{22.8170, 108.3665});
        PROVINCES.put("新疆", new double[]{43.8266, 87.6168});
        PROVINCES.put("江苏", new double[]{32.0603, 118.7969});
        PROVINCES.put("江西", new double[]{28.6820, 115.8579});
        PROVINCES.put("河北", new double[]{38.0428, 114.5149});
        PROVINCES.put("河南", new double[]{34.7466, 113.6254});
        PROVINCES.put("浙江", new double[]{30.2741, 120.1551});
        PROVINCES.put("海南", new double[]{20.0442, 110.1999});
        PROVINCES.put("湖北", new double[]{30.5931, 114.3054});
        PROVINCES.put("湖南", new double[]{28.2282, 112.9388});
        PROVINCES.put("澳门", new double[]{22.1987, 113.5439});
        PROVINCES.put("甘肃", new double[]{36.0611, 103.8343});
        PROVINCES.put("福建", new double[]{26.0745, 119.2965});
        PROVINCES.put("西藏", new double[]{29.6450, 91.1403});
        PROVINCES.put("贵州", new double[]{26.6477, 106.6302});
        PROVINCES.put("辽宁", new double[]{41.8057, 123.4315});
        PROVINCES.put("重庆", new double[]{29.5630, 106.5516});
        PROVINCES.put("陕西", new double[]{34.3416, 108.9398});
        PROVINCES.put("青海", new double[]{36.6171, 101.7782});
        PROVINCES.put("香港", new double[]{22.3193, 114.1694});
        PROVINCES.put("黑龙江", new double[]{45.8038, 126.5350});
    }

    /** 省+市（任一可为空）→ {lat,lng}；都查不到返回 null */
    static double[] lookup(String province, String city) {
        double[] c = null;
        if (city != null && !city.isEmpty()) {
            c = CITIES.get(normCity(city));
            if (c == null) c = PROVINCES.get(normProvince(city));
        }
        if (c != null) return c;
        if (province != null && !province.isEmpty()) {
            double[] p = PROVINCES.get(normProvince(province));
            if (p != null) return p;
        }
        return null;
    }

    /** v9.31：关键词模糊检索（市+省），返回 {显示名, lat, lng} 列表，按名长排序 */
    static List<String[]> fuzzySearch(String q) {
        List<String[]> out = new ArrayList<String[]>();
        String t = q == null ? "" : q.trim();
        if (t.isEmpty()) return out;
        // v9.47：省+市 切分——「浙江温州」先识别省词「浙江」，剩余「温州」按市匹配；
        // 根除带省前缀输入匹配不到市的问题（此前必须输「温州市」才显示）
        String provWord = null;
        String cityPart = t;
        for (String pk : PROVINCES.keySet()) {
            if (t.length() > pk.length() && t.startsWith(pk)) {
                // 取最长省词（省表 key 均为完整省名，防短前缀误截断）
                if (provWord == null || pk.length() > provWord.length()) {
                    provWord = pk;
                    cityPart = t.substring(pk.length());
                }
            }
        }
        if (provWord != null) {
            // 去掉「省/市/自治区/特别行政区」等连接后缀：「浙江省温州」->「温州」
            for (String pre : new String[]{"壮族自治区", "回族自治区", "维吾尔自治区",
                    "自治区", "特别行政区", "省", "市"}) {
                if (cityPart.startsWith(pre)) {
                    cityPart = cityPart.substring(pre.length());
                    break;
                }
            }
            // 剩余为空（如「上海市」）则退回原逻辑（原逻辑本身可命中直辖市）
            if (!cityPart.isEmpty()) {
                String ncp = normCity(cityPart);
                for (String k : CITIES.keySet()) {
                    if (normCity(k).contains(ncp) || k.contains(cityPart)) {
                        double[] c = CITIES.get(k);
                        out.add(new String[]{k, String.valueOf(c[0]), String.valueOf(c[1])});
                    }
                }
                java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
                    @Override
                    public int compare(String[] a, String[] b) {
                        return Integer.compare(a[0].length(), b[0].length());
                    }
                });
                return out;
            }
        }
        String nq = normCity(t);
        for (String k : CITIES.keySet()) {
            if (normCity(k).contains(nq) || k.contains(t)) {
                double[] c = CITIES.get(k);
                out.add(new String[]{k, String.valueOf(c[0]), String.valueOf(c[1])});
            }
        }
        String np = normProvince(t);
        for (String k : PROVINCES.keySet()) {
            if (normProvince(k).contains(np) || k.contains(t)) {
                double[] c = PROVINCES.get(k);
                out.add(new String[]{k, String.valueOf(c[0]), String.valueOf(c[1])});
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Integer.compare(a[0].length(), b[0].length());
            }
        });
        return out;
    }

    /** v9.51：市名（容忍后缀）-> 所属省全称；查不到返回 null */
    public static String provinceOf(String cityName) {
        if (cityName == null) return null;
        // 完整名直查优先：「省直辖县级行政区划」「大兴安岭地区」等会被 normCity 误伤后缀
        String direct = CITY_PROV.get(cityName);
        if (direct != null) return direct;
        return CITY_PROV.get(normCity(cityName));
    }

    /** v9.51：市 key 规范化显示：「温州」->「温州市」；自治州/盟/地区原样返回。
     *  v9.59 修复：补「市」后缀豁免——DistrictTable 区县数据所属市带「市」（如「深圳市」），
     *  此前会二次加「市」拼出「深圳市市」叠字。 */
    public static String cityFull(String key) {
        if (key == null || key.isEmpty()) return key;
        // v9.51 修正：只认「自治州」，避免「杭州/温州/广州」被误判为自治州不加「市」
        for (String suf : new String[]{"市", "自治州", "盟", "地区", "行政区划", "林区", "县", "区"}) {
            if (key.endsWith(suf)) return key;
        }
        return key + "市";
    }

    /** v9.51：省 key -> 标准全称（「浙江」->「浙江省」）；查不到返回原串 */
    public static String provinceFull(String provKey) {
        String f = PROV_FULL.get(provKey);
        return f != null ? f : provKey;
    }

    /** v9.51：输入是否为省名（容忍后缀：「浙江」「浙江省」都命中）-> 返回省 key 或 null */
    public static String provinceMatch(String t) {
        if (t == null) return null;
        String np = normProvince(t);
        if (np.isEmpty()) return null;
        for (String k : PROVINCES.keySet()) {
            if (normProvince(k).equals(np)) return k;
        }
        return null;
    }

    /** v9.51：输入是否为纯市名（「温州」「温州市」）-> 返回 {省全称, 市全称} 或 null */
    public static String[] cityMatch(String t) {
        if (t == null) return null;
        String c = normCity(t);
        if (c.isEmpty()) return null;
        String prov = CITY_PROV.get(c);
        if (prov == null) return null;
        if (t.length() > c.length() + 1) return null;   // 「温州鹿城」等复合输入不走市级联
        return new String[]{prov, cityFull(c)};
    }

    /** v9.51：省 key（无后缀）-> 省坐标（PlaceSearch 级联首条用） */
    public static double[] provinceCoord(String provKey) {
        return PROVINCES.get(provKey);
    }

    /** v9.51：市名（容忍后缀「温州市」）-> 市坐标；查不到返回 null */
    public static double[] cityCoord(String cityFullName) {
        if (cityFullName == null) return null;
        double[] d = CITIES.get(cityFullName);
        if (d != null) return d;
        return CITIES.get(normCity(cityFullName));
    }

    /** v9.51：省全称 -> 该省所有地级市候选（直辖市省略，由调用方补区县） */
    public static java.util.List<String[]> listCitiesOf(String provFull) {
        java.util.List<String[]> out = new java.util.ArrayList<String[]>();
        for (String key : CITY_PROV.keySet()) {
            if (!provFull.equals(CITY_PROV.get(key))) continue;
            if ("省直辖县级行政区划".equals(key)) continue;              // 非实体市
            if (key.equals("北京") || key.equals("天津") || key.equals("上海")
                    || key.equals("重庆")) continue;                    // 直辖市：下一级是区
            double[] c = CITIES.get(key);
            if (c == null) continue;
            out.add(new String[]{provFull + " · " + cityFull(key),
                    String.valueOf(c[0]), String.valueOf(c[1])});
        }
        return out;
    }

    /** 城市名去后缀："深圳市"->"深圳"；"湘西土家族苗族自治州"->"湘西"（查不到表再回退省） */
    private static String normCity(String s) {
        String t = s.trim();
        for (String suf : new String[]{"市", "地区", "自治州", "盟", "县", "区", "林区"}) {
            if (t.endsWith(suf)) t = t.substring(0, t.length() - suf.length());
        }
        return t;
    }

    /** 省名去后缀："广东省"->"广东"；"广西壮族自治区"->"广西"；"新疆维吾尔自治区"->"新疆" */

    /** v9.51：省 key（无后缀）-> 标准显示全称 */
    private static final HashMap<String, String> PROV_FULL = new HashMap<String, String>();
    static {
        PROV_FULL.put("北京", "北京市"); PROV_FULL.put("天津", "天津市");
        PROV_FULL.put("上海", "上海市"); PROV_FULL.put("重庆", "重庆市");
        PROV_FULL.put("河北", "河北省"); PROV_FULL.put("山西", "山西省");
        PROV_FULL.put("辽宁", "辽宁省"); PROV_FULL.put("吉林", "吉林省");
        PROV_FULL.put("黑龙江", "黑龙江省"); PROV_FULL.put("江苏", "江苏省");
        PROV_FULL.put("浙江", "浙江省"); PROV_FULL.put("安徽", "安徽省");
        PROV_FULL.put("福建", "福建省"); PROV_FULL.put("江西", "江西省");
        PROV_FULL.put("山东", "山东省"); PROV_FULL.put("河南", "河南省");
        PROV_FULL.put("湖北", "湖北省"); PROV_FULL.put("湖南", "湖南省");
        PROV_FULL.put("广东", "广东省"); PROV_FULL.put("海南", "海南省");
        PROV_FULL.put("四川", "四川省"); PROV_FULL.put("贵州", "贵州省");
        PROV_FULL.put("云南", "云南省"); PROV_FULL.put("陕西", "陕西省");
        PROV_FULL.put("甘肃", "甘肃省"); PROV_FULL.put("青海", "青海省");
        PROV_FULL.put("台湾", "台湾省"); PROV_FULL.put("内蒙古", "内蒙古自治区");
        PROV_FULL.put("广西", "广西壮族自治区"); PROV_FULL.put("西藏", "西藏自治区");
        PROV_FULL.put("宁夏", "宁夏回族自治区"); PROV_FULL.put("新疆", "新疆维吾尔自治区");
        PROV_FULL.put("香港", "香港特别行政区"); PROV_FULL.put("澳门", "澳门特别行政区");
    }

    /** v9.51：市（无后缀）-> 所属省标准全称（354 地级行政区） */
    private static final HashMap<String, String> CITY_PROV = new HashMap<String, String>();
    static {
        CITY_PROV.put("北京", "北京市");
        CITY_PROV.put("天津", "天津市");
        CITY_PROV.put("上海", "上海市");
        CITY_PROV.put("重庆", "重庆市");
        CITY_PROV.put("石家庄", "河北省");
        CITY_PROV.put("唐山", "河北省");
        CITY_PROV.put("秦皇岛", "河北省");
        CITY_PROV.put("邯郸", "河北省");
        CITY_PROV.put("邢台", "河北省");
        CITY_PROV.put("保定", "河北省");
        CITY_PROV.put("张家口", "河北省");
        CITY_PROV.put("承德", "河北省");
        CITY_PROV.put("沧州", "河北省");
        CITY_PROV.put("廊坊", "河北省");
        CITY_PROV.put("衡水", "河北省");
        CITY_PROV.put("太原", "山西省");
        CITY_PROV.put("大同", "山西省");
        CITY_PROV.put("阳泉", "山西省");
        CITY_PROV.put("长治", "山西省");
        CITY_PROV.put("晋城", "山西省");
        CITY_PROV.put("朔州", "山西省");
        CITY_PROV.put("晋中", "山西省");
        CITY_PROV.put("运城", "山西省");
        CITY_PROV.put("忻州", "山西省");
        CITY_PROV.put("临汾", "山西省");
        CITY_PROV.put("吕梁", "山西省");
        CITY_PROV.put("呼和浩特", "内蒙古自治区");
        CITY_PROV.put("包头", "内蒙古自治区");
        CITY_PROV.put("乌海", "内蒙古自治区");
        CITY_PROV.put("赤峰", "内蒙古自治区");
        CITY_PROV.put("通辽", "内蒙古自治区");
        CITY_PROV.put("鄂尔多斯", "内蒙古自治区");
        CITY_PROV.put("呼伦贝尔", "内蒙古自治区");
        CITY_PROV.put("巴彦淖尔", "内蒙古自治区");
        CITY_PROV.put("乌兰察布", "内蒙古自治区");
        CITY_PROV.put("兴安盟", "内蒙古自治区");
        CITY_PROV.put("锡林郭勒盟", "内蒙古自治区");
        CITY_PROV.put("阿拉善盟", "内蒙古自治区");
        CITY_PROV.put("沈阳", "辽宁省");
        CITY_PROV.put("大连", "辽宁省");
        CITY_PROV.put("鞍山", "辽宁省");
        CITY_PROV.put("抚顺", "辽宁省");
        CITY_PROV.put("本溪", "辽宁省");
        CITY_PROV.put("丹东", "辽宁省");
        CITY_PROV.put("锦州", "辽宁省");
        CITY_PROV.put("营口", "辽宁省");
        CITY_PROV.put("阜新", "辽宁省");
        CITY_PROV.put("辽阳", "辽宁省");
        CITY_PROV.put("盘锦", "辽宁省");
        CITY_PROV.put("铁岭", "辽宁省");
        CITY_PROV.put("朝阳", "辽宁省");
        CITY_PROV.put("葫芦岛", "辽宁省");
        CITY_PROV.put("长春", "吉林省");
        CITY_PROV.put("吉林", "吉林省");
        CITY_PROV.put("四平", "吉林省");
        CITY_PROV.put("辽源", "吉林省");
        CITY_PROV.put("通化", "吉林省");
        CITY_PROV.put("白山", "吉林省");
        CITY_PROV.put("松原", "吉林省");
        CITY_PROV.put("白城", "吉林省");
        CITY_PROV.put("延边朝鲜族自治州", "吉林省");
        CITY_PROV.put("哈尔滨", "黑龙江省");
        CITY_PROV.put("齐齐哈尔", "黑龙江省");
        CITY_PROV.put("鸡西", "黑龙江省");
        CITY_PROV.put("鹤岗", "黑龙江省");
        CITY_PROV.put("双鸭山", "黑龙江省");
        CITY_PROV.put("大庆", "黑龙江省");
        CITY_PROV.put("伊春", "黑龙江省");
        CITY_PROV.put("佳木斯", "黑龙江省");
        CITY_PROV.put("七台河", "黑龙江省");
        CITY_PROV.put("牡丹江", "黑龙江省");
        CITY_PROV.put("黑河", "黑龙江省");
        CITY_PROV.put("绥化", "黑龙江省");
        CITY_PROV.put("大兴安岭地区", "黑龙江省");
        CITY_PROV.put("南京", "江苏省");
        CITY_PROV.put("无锡", "江苏省");
        CITY_PROV.put("徐州", "江苏省");
        CITY_PROV.put("常州", "江苏省");
        CITY_PROV.put("苏州", "江苏省");
        CITY_PROV.put("南通", "江苏省");
        CITY_PROV.put("连云港", "江苏省");
        CITY_PROV.put("淮安", "江苏省");
        CITY_PROV.put("盐城", "江苏省");
        CITY_PROV.put("扬州", "江苏省");
        CITY_PROV.put("镇江", "江苏省");
        CITY_PROV.put("泰州", "江苏省");
        CITY_PROV.put("宿迁", "江苏省");
        CITY_PROV.put("杭州", "浙江省");
        CITY_PROV.put("宁波", "浙江省");
        CITY_PROV.put("温州", "浙江省");
        CITY_PROV.put("嘉兴", "浙江省");
        CITY_PROV.put("湖州", "浙江省");
        CITY_PROV.put("绍兴", "浙江省");
        CITY_PROV.put("金华", "浙江省");
        CITY_PROV.put("衢州", "浙江省");
        CITY_PROV.put("舟山", "浙江省");
        CITY_PROV.put("台州", "浙江省");
        CITY_PROV.put("丽水", "浙江省");
        CITY_PROV.put("合肥", "安徽省");
        CITY_PROV.put("芜湖", "安徽省");
        CITY_PROV.put("蚌埠", "安徽省");
        CITY_PROV.put("淮南", "安徽省");
        CITY_PROV.put("马鞍山", "安徽省");
        CITY_PROV.put("淮北", "安徽省");
        CITY_PROV.put("铜陵", "安徽省");
        CITY_PROV.put("安庆", "安徽省");
        CITY_PROV.put("黄山", "安徽省");
        CITY_PROV.put("滁州", "安徽省");
        CITY_PROV.put("阜阳", "安徽省");
        CITY_PROV.put("宿州", "安徽省");
        CITY_PROV.put("六安", "安徽省");
        CITY_PROV.put("亳州", "安徽省");
        CITY_PROV.put("池州", "安徽省");
        CITY_PROV.put("宣城", "安徽省");
        CITY_PROV.put("福州", "福建省");
        CITY_PROV.put("厦门", "福建省");
        CITY_PROV.put("莆田", "福建省");
        CITY_PROV.put("三明", "福建省");
        CITY_PROV.put("泉州", "福建省");
        CITY_PROV.put("漳州", "福建省");
        CITY_PROV.put("南平", "福建省");
        CITY_PROV.put("龙岩", "福建省");
        CITY_PROV.put("宁德", "福建省");
        CITY_PROV.put("南昌", "江西省");
        CITY_PROV.put("景德镇", "江西省");
        CITY_PROV.put("萍乡", "江西省");
        CITY_PROV.put("九江", "江西省");
        CITY_PROV.put("新余", "江西省");
        CITY_PROV.put("鹰潭", "江西省");
        CITY_PROV.put("赣州", "江西省");
        CITY_PROV.put("吉安", "江西省");
        CITY_PROV.put("宜春", "江西省");
        CITY_PROV.put("抚州", "江西省");
        CITY_PROV.put("上饶", "江西省");
        CITY_PROV.put("济南", "山东省");
        CITY_PROV.put("青岛", "山东省");
        CITY_PROV.put("淄博", "山东省");
        CITY_PROV.put("枣庄", "山东省");
        CITY_PROV.put("东营", "山东省");
        CITY_PROV.put("烟台", "山东省");
        CITY_PROV.put("潍坊", "山东省");
        CITY_PROV.put("济宁", "山东省");
        CITY_PROV.put("泰安", "山东省");
        CITY_PROV.put("威海", "山东省");
        CITY_PROV.put("日照", "山东省");
        CITY_PROV.put("临沂", "山东省");
        CITY_PROV.put("德州", "山东省");
        CITY_PROV.put("聊城", "山东省");
        CITY_PROV.put("滨州", "山东省");
        CITY_PROV.put("菏泽", "山东省");
        CITY_PROV.put("郑州", "河南省");
        CITY_PROV.put("开封", "河南省");
        CITY_PROV.put("洛阳", "河南省");
        CITY_PROV.put("平顶山", "河南省");
        CITY_PROV.put("安阳", "河南省");
        CITY_PROV.put("鹤壁", "河南省");
        CITY_PROV.put("新乡", "河南省");
        CITY_PROV.put("焦作", "河南省");
        CITY_PROV.put("濮阳", "河南省");
        CITY_PROV.put("许昌", "河南省");
        CITY_PROV.put("漯河", "河南省");
        CITY_PROV.put("三门峡", "河南省");
        CITY_PROV.put("南阳", "河南省");
        CITY_PROV.put("商丘", "河南省");
        CITY_PROV.put("信阳", "河南省");
        CITY_PROV.put("周口", "河南省");
        CITY_PROV.put("驻马店", "河南省");
        CITY_PROV.put("济源", "河南省");
        CITY_PROV.put("武汉", "湖北省");
        CITY_PROV.put("黄石", "湖北省");
        CITY_PROV.put("十堰", "湖北省");
        CITY_PROV.put("宜昌", "湖北省");
        CITY_PROV.put("襄阳", "湖北省");
        CITY_PROV.put("鄂州", "湖北省");
        CITY_PROV.put("荆门", "湖北省");
        CITY_PROV.put("孝感", "湖北省");
        CITY_PROV.put("荆州", "湖北省");
        CITY_PROV.put("黄冈", "湖北省");
        CITY_PROV.put("咸宁", "湖北省");
        CITY_PROV.put("随州", "湖北省");
        CITY_PROV.put("恩施土家族苗族自治州", "湖北省");
        CITY_PROV.put("仙桃", "湖北省");
        CITY_PROV.put("潜江", "湖北省");
        CITY_PROV.put("天门", "湖北省");
        CITY_PROV.put("神农架林区", "湖北省");
        CITY_PROV.put("长沙", "湖南省");
        CITY_PROV.put("株洲", "湖南省");
        CITY_PROV.put("湘潭", "湖南省");
        CITY_PROV.put("衡阳", "湖南省");
        CITY_PROV.put("邵阳", "湖南省");
        CITY_PROV.put("岳阳", "湖南省");
        CITY_PROV.put("常德", "湖南省");
        CITY_PROV.put("张家界", "湖南省");
        CITY_PROV.put("益阳", "湖南省");
        CITY_PROV.put("郴州", "湖南省");
        CITY_PROV.put("永州", "湖南省");
        CITY_PROV.put("怀化", "湖南省");
        CITY_PROV.put("娄底", "湖南省");
        CITY_PROV.put("湘西土家族苗族自治州", "湖南省");
        CITY_PROV.put("广州", "广东省");
        CITY_PROV.put("韶关", "广东省");
        CITY_PROV.put("深圳", "广东省");
        CITY_PROV.put("珠海", "广东省");
        CITY_PROV.put("汕头", "广东省");
        CITY_PROV.put("佛山", "广东省");
        CITY_PROV.put("江门", "广东省");
        CITY_PROV.put("湛江", "广东省");
        CITY_PROV.put("茂名", "广东省");
        CITY_PROV.put("肇庆", "广东省");
        CITY_PROV.put("惠州", "广东省");
        CITY_PROV.put("梅州", "广东省");
        CITY_PROV.put("汕尾", "广东省");
        CITY_PROV.put("河源", "广东省");
        CITY_PROV.put("阳江", "广东省");
        CITY_PROV.put("清远", "广东省");
        CITY_PROV.put("东莞", "广东省");
        CITY_PROV.put("中山", "广东省");
        CITY_PROV.put("潮州", "广东省");
        CITY_PROV.put("揭阳", "广东省");
        CITY_PROV.put("云浮", "广东省");
        CITY_PROV.put("南宁", "广西壮族自治区");
        CITY_PROV.put("柳州", "广西壮族自治区");
        CITY_PROV.put("桂林", "广西壮族自治区");
        CITY_PROV.put("梧州", "广西壮族自治区");
        CITY_PROV.put("北海", "广西壮族自治区");
        CITY_PROV.put("防城港", "广西壮族自治区");
        CITY_PROV.put("钦州", "广西壮族自治区");
        CITY_PROV.put("贵港", "广西壮族自治区");
        CITY_PROV.put("玉林", "广西壮族自治区");
        CITY_PROV.put("百色", "广西壮族自治区");
        CITY_PROV.put("贺州", "广西壮族自治区");
        CITY_PROV.put("河池", "广西壮族自治区");
        CITY_PROV.put("来宾", "广西壮族自治区");
        CITY_PROV.put("崇左", "广西壮族自治区");
        CITY_PROV.put("海口", "海南省");
        CITY_PROV.put("三亚", "海南省");
        CITY_PROV.put("三沙", "海南省");
        CITY_PROV.put("儋州", "海南省");
        CITY_PROV.put("省直辖县级行政区划", "海南省");
        CITY_PROV.put("成都", "四川省");
        CITY_PROV.put("自贡", "四川省");
        CITY_PROV.put("攀枝花", "四川省");
        CITY_PROV.put("泸州", "四川省");
        CITY_PROV.put("德阳", "四川省");
        CITY_PROV.put("绵阳", "四川省");
        CITY_PROV.put("广元", "四川省");
        CITY_PROV.put("遂宁", "四川省");
        CITY_PROV.put("内江", "四川省");
        CITY_PROV.put("乐山", "四川省");
        CITY_PROV.put("南充", "四川省");
        CITY_PROV.put("眉山", "四川省");
        CITY_PROV.put("宜宾", "四川省");
        CITY_PROV.put("广安", "四川省");
        CITY_PROV.put("达州", "四川省");
        CITY_PROV.put("雅安", "四川省");
        CITY_PROV.put("巴中", "四川省");
        CITY_PROV.put("资阳", "四川省");
        CITY_PROV.put("阿坝藏族羌族自治州", "四川省");
        CITY_PROV.put("甘孜藏族自治州", "四川省");
        CITY_PROV.put("凉山彝族自治州", "四川省");
        CITY_PROV.put("贵阳", "贵州省");
        CITY_PROV.put("六盘水", "贵州省");
        CITY_PROV.put("遵义", "贵州省");
        CITY_PROV.put("安顺", "贵州省");
        CITY_PROV.put("毕节", "贵州省");
        CITY_PROV.put("铜仁", "贵州省");
        CITY_PROV.put("黔西南布依族苗族自治州", "贵州省");
        CITY_PROV.put("黔东南苗族侗族自治州", "贵州省");
        CITY_PROV.put("黔南布依族苗族自治州", "贵州省");
        CITY_PROV.put("昆明", "云南省");
        CITY_PROV.put("曲靖", "云南省");
        CITY_PROV.put("玉溪", "云南省");
        CITY_PROV.put("保山", "云南省");
        CITY_PROV.put("昭通", "云南省");
        CITY_PROV.put("丽江", "云南省");
        CITY_PROV.put("普洱", "云南省");
        CITY_PROV.put("临沧", "云南省");
        CITY_PROV.put("楚雄彝族自治州", "云南省");
        CITY_PROV.put("红河哈尼族彝族自治州", "云南省");
        CITY_PROV.put("文山壮族苗族自治州", "云南省");
        CITY_PROV.put("西双版纳傣族自治州", "云南省");
        CITY_PROV.put("大理白族自治州", "云南省");
        CITY_PROV.put("德宏傣族景颇族自治州", "云南省");
        CITY_PROV.put("怒江傈僳族自治州", "云南省");
        CITY_PROV.put("迪庆藏族自治州", "云南省");
        CITY_PROV.put("拉萨", "西藏自治区");
        CITY_PROV.put("日喀则", "西藏自治区");
        CITY_PROV.put("昌都", "西藏自治区");
        CITY_PROV.put("林芝", "西藏自治区");
        CITY_PROV.put("山南", "西藏自治区");
        CITY_PROV.put("那曲", "西藏自治区");
        CITY_PROV.put("阿里地区", "西藏自治区");
        CITY_PROV.put("西安", "陕西省");
        CITY_PROV.put("铜川", "陕西省");
        CITY_PROV.put("宝鸡", "陕西省");
        CITY_PROV.put("咸阳", "陕西省");
        CITY_PROV.put("渭南", "陕西省");
        CITY_PROV.put("延安", "陕西省");
        CITY_PROV.put("汉中", "陕西省");
        CITY_PROV.put("榆林", "陕西省");
        CITY_PROV.put("安康", "陕西省");
        CITY_PROV.put("商洛", "陕西省");
        CITY_PROV.put("兰州", "甘肃省");
        CITY_PROV.put("嘉峪关", "甘肃省");
        CITY_PROV.put("金昌", "甘肃省");
        CITY_PROV.put("白银", "甘肃省");
        CITY_PROV.put("天水", "甘肃省");
        CITY_PROV.put("武威", "甘肃省");
        CITY_PROV.put("张掖", "甘肃省");
        CITY_PROV.put("平凉", "甘肃省");
        CITY_PROV.put("酒泉", "甘肃省");
        CITY_PROV.put("庆阳", "甘肃省");
        CITY_PROV.put("定西", "甘肃省");
        CITY_PROV.put("陇南", "甘肃省");
        CITY_PROV.put("临夏回族自治州", "甘肃省");
        CITY_PROV.put("甘南藏族自治州", "甘肃省");
        CITY_PROV.put("西宁", "青海省");
        CITY_PROV.put("海东", "青海省");
        CITY_PROV.put("海北藏族自治州", "青海省");
        CITY_PROV.put("黄南藏族自治州", "青海省");
        CITY_PROV.put("海南藏族自治州", "青海省");
        CITY_PROV.put("果洛藏族自治州", "青海省");
        CITY_PROV.put("玉树藏族自治州", "青海省");
        CITY_PROV.put("海西蒙古族藏族自治州", "青海省");
        CITY_PROV.put("银川", "宁夏回族自治区");
        CITY_PROV.put("石嘴山", "宁夏回族自治区");
        CITY_PROV.put("吴忠", "宁夏回族自治区");
        CITY_PROV.put("固原", "宁夏回族自治区");
        CITY_PROV.put("中卫", "宁夏回族自治区");
        CITY_PROV.put("乌鲁木齐", "新疆维吾尔自治区");
        CITY_PROV.put("克拉玛依", "新疆维吾尔自治区");
        CITY_PROV.put("吐鲁番", "新疆维吾尔自治区");
        CITY_PROV.put("哈密", "新疆维吾尔自治区");
        CITY_PROV.put("昌吉回族自治州", "新疆维吾尔自治区");
        CITY_PROV.put("博尔塔拉蒙古自治州", "新疆维吾尔自治区");
        CITY_PROV.put("巴音郭楞蒙古自治州", "新疆维吾尔自治区");
        CITY_PROV.put("阿克苏地区", "新疆维吾尔自治区");
        CITY_PROV.put("克孜勒苏柯尔克孜自治州", "新疆维吾尔自治区");
        CITY_PROV.put("喀什地区", "新疆维吾尔自治区");
        CITY_PROV.put("和田地区", "新疆维吾尔自治区");
        CITY_PROV.put("伊犁哈萨克自治州", "新疆维吾尔自治区");
        CITY_PROV.put("塔城地区", "新疆维吾尔自治区");
        CITY_PROV.put("阿勒泰地区", "新疆维吾尔自治区");
        CITY_PROV.put("石河子", "新疆维吾尔自治区");
        CITY_PROV.put("阿拉尔", "新疆维吾尔自治区");
        CITY_PROV.put("图木舒克", "新疆维吾尔自治区");
        CITY_PROV.put("五家渠", "新疆维吾尔自治区");
        CITY_PROV.put("北屯", "新疆维吾尔自治区");
        CITY_PROV.put("铁门关", "新疆维吾尔自治区");
        CITY_PROV.put("双河", "新疆维吾尔自治区");
        CITY_PROV.put("可克达拉", "新疆维吾尔自治区");
        CITY_PROV.put("昆玉", "新疆维吾尔自治区");
        CITY_PROV.put("胡杨河", "新疆维吾尔自治区");
        CITY_PROV.put("新星", "新疆维吾尔自治区");    }

    private static String normProvince(String s) {
        String t = s.trim();
        for (String suf : new String[]{"省", "市", "自治区", "特别行政区", "壮族", "回族", "维吾尔"}) {
            if (t.endsWith(suf)) t = t.substring(0, t.length() - suf.length());
        }
        return t;
    }
}