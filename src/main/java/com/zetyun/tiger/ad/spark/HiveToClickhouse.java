package com.zetyun.tiger.ad.spark;

import org.apache.commons.cli.*;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
public class HiveToClickhouse {

    public static void main(String[] args) {

        //使用 commons-cli 解析参数
        //1.定义参数
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("hive_db").withDescription("hive数据库名称(required)").hasArg(true).isRequired(true).create());
        options.addOption(OptionBuilder.withLongOpt("hive_table").withDescription("hive表名称(required)").hasArg(true).isRequired(true).create());
        options.addOption(OptionBuilder.withLongOpt("hive_partition").withDescription("hive分区(required)").hasArg(true).isRequired(true).create());
        options.addOption(OptionBuilder.withLongOpt("ck_url").withDescription("clickhouse的jdbc url(required)").hasArg(true).isRequired(true).create());

        options.addOption(OptionBuilder.withLongOpt("ck_user").withDescription("clickhouse的用户名(required)").hasArg(true).isRequired(true).create());
        options.addOption(OptionBuilder.withLongOpt("ck_password").withDescription("clickhouse密码(required)").hasArg(true).isRequired(true).create());


        options.addOption(OptionBuilder.withLongOpt("ck_table").withDescription("clickhouse表名称(required)").hasArg(true).isRequired(true).create());
        options.addOption(OptionBuilder.withLongOpt("batch_size").withDescription("数据写入clickhouse时的批次大小(required)").hasArg(true).isRequired(true).create());

        //2.解析参数
        CommandLineParser parser = new GnuParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            //若catch到参数解析异常(即传入的参数非法),则打印帮助信息,并return
            System.out.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("--option argument", options);
            return;
        }

        //3.创建SparkConf
        SparkConf sparkConf = new SparkConf().setAppName("hive2clickhouse").setMaster("local[1]");

        //4.创建SparkSession,并启动Hive支持
        SparkSession sparkSession = SparkSession.builder().enableHiveSupport().config(sparkConf).getOrCreate();

        //5.设置如下参数,支持使用正则表达式匹配查询字段
        sparkSession.sql("set spark.sql.parser.quotedRegexColumnNames=true");

        //6.执行如下查询语句,查询hive表中除去dt分区字段外的所有字段
        String sql = "select `(dt)?+.+` from " + cmd.getOptionValue("hive_db") + "." + cmd.getOptionValue("hive_table") + " where dt='" + cmd.getOptionValue("hive_partition") + "'";
        Dataset<Row> hive = sparkSession.sql(sql);

        //7.将数据通过jdbc模式写入clickhouse
        hive.write().mode(SaveMode.Append)
                .format("jdbc")
                .option("url", cmd.getOptionValue("ck_url"))
                .option("user", cmd.getOptionValue("ck_user"))
                .option("password", cmd.getOptionValue("ck_password"))
                .option("dbtable", cmd.getOptionValue("ck_table"))
                .option("driver", "ru.yandex.clickhouse.ClickHouseDriver")
                .option("batchsize", cmd.getOptionValue("batch_size"))
                .save();

        //8.关闭SparkSession
        sparkSession.close();
    }
}
