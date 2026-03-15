package com.gabon.common.util;

import java.util.ArrayList;
import java.util.List;

/**


 **/

public class ShardingDBConfig {

    /**
     * 存储数据库位置编号
     */
    private static final List<String> dbPrefixList = new ArrayList<>();


    //配置启用那些库的前缀
    static {
        dbPrefixList.add("0");
        dbPrefixList.add("1");
        dbPrefixList.add("a");


//        dbPrefixList.add("b");
//        dbPrefixList.add("c");
//        dbPrefixList.add("b");
//        dbPrefixList.add("c");
//        dbPrefixList.add("b");
//        dbPrefixList.add("c");
//        dbPrefixList.add("b");
//        dbPrefixList.add("c");
    }


    /**
     * 获取随机的前缀
     * @return
     */
    public static String getRandomDBPrefix(String code){

        int hashCode = code.hashCode();

        int index = Math.abs(hashCode) % dbPrefixList.size();

        return dbPrefixList.get(index);
    }



}
