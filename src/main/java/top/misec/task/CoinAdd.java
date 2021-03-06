package top.misec.task;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import top.misec.apiquery.ApiList;
import top.misec.apiquery.OftenAPI;
import top.misec.config.Config;
import top.misec.login.Verify;
import top.misec.utils.HttpUtil;

import java.util.Random;

import static top.misec.task.TaskInfoHolder.getVideoId;
import static top.misec.task.TaskInfoHolder.statusCodeStr;

/**
 * 投币任务
 *
 * @author cmcc
 * @since 2020-11-22 5:28
 */

public class CoinAdd implements Task {

    static Logger logger = (Logger) LogManager.getLogger(CoinAdd.class.getName());

    private final String taskName = "投币任务";

    @Override
    public void run() {

        //投币最多操作数 解决csrf校验失败时死循环的问题
        int addCoinOperateCount = 0;
        //安全检查，最多投币数
        final int maxNumberOfCoins = 5;
        //获取自定义配置投币数 配置写在src/main/resources/config.json中
        int setCoin = Config.getInstance().getNumberOfCoins();
        //已投的硬币
        int useCoin = TaskInfoHolder.expConfirm();
        //投币策略
        int coinAddPriority = Config.getInstance().getCoinAddPriority();

        if (setCoin > maxNumberOfCoins) {
            logger.info("自定义投币数为: " + setCoin + "枚," + "为保护你的资产，自定义投币数重置为: " + maxNumberOfCoins + "枚");
            setCoin = maxNumberOfCoins;
        }

        logger.info("自定义投币数为: " + setCoin + "枚," + "程序执行前已投: " + useCoin + "枚");

        //调整投币数 设置投币数-已经投过的硬币数
        int needCoins = setCoin - useCoin;

        //投币前硬币余额
        Double beforeAddCoinBalance = OftenAPI.getCoinBalance();
        int coinBalance = (int) Math.floor(beforeAddCoinBalance);

        if (needCoins <= 0) {
            logger.info("已完成设定的投币任务，今日无需再投币了");
        } else {
            logger.info("投币数调整为: " + needCoins + "枚");
            //投币数大于余额时，按余额投
            if (needCoins > coinBalance) {
                logger.info("完成今日设定投币任务还需要投: " + needCoins + "枚硬币，但是余额只有: " + beforeAddCoinBalance);
                logger.info("投币数调整为: " + coinBalance);
                needCoins = coinBalance;
            }
        }

        logger.info("投币前余额为 : " + beforeAddCoinBalance);
        /*
         * 开始投币
         * 请勿修改 max_numberOfCoins 这里多判断一次保证投币数超过5时 不执行投币操作
         * 最后一道安全判断，保证即使前面的判断逻辑错了，也不至于发生投币事故
         */
        while (needCoins > 0 && needCoins <= maxNumberOfCoins) {
            String bvid;

            if (coinAddPriority == 1 && addCoinOperateCount < 7) {
                bvid = getVideoId.getFollowUpRandomVideoBvid();
            } else {
                bvid = getVideoId.getRegionRankingVideoBvid();
            }

            addCoinOperateCount++;
            boolean flag = coinAdd(bvid, 1, Config.getInstance().getSelectLike());
            if (flag) {
                try {
                    Random random = new Random();
                    int sleepTime = random.nextInt(1000) + 2000;
                    logger.info("投币后随机暂停" + sleepTime + "毫秒");
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                needCoins--;
            }
            if (addCoinOperateCount > 15) {
                logger.info("尝试投币/投币失败次数太多");
                break;
            }
        }
        logger.info("投币任务完成后余额为: " + OftenAPI.getCoinBalance());
    }

    /**
     * @param bvid       av号
     * @param multiply   投币数量
     * @param selectLike 是否同时点赞 1是
     * @return 是否投币成功
     */
    private boolean coinAdd(String bvid, int multiply, int selectLike) {
        String requestBody = "bvid=" + bvid
                + "&multiply=" + multiply
                + "&select_like=" + selectLike
                + "&cross_domain=" + "true"
                + "&csrf=" + Verify.getInstance().getBiliJct();
        String videoTitle = OftenAPI.videoTitle(bvid);
        //判断曾经是否对此av投币过
        if (!isCoin(bvid)) {
            JsonObject jsonObject = HttpUtil.doPost(ApiList.CoinAdd, requestBody);
            if (jsonObject.get(statusCodeStr).getAsInt() == 0) {

                logger.info("为 " + videoTitle + " 投币成功");
                return true;
            } else {
                logger.info("投币失败" + jsonObject.get("message").getAsString());
                return false;
            }
        } else {
            logger.debug("已经为" + videoTitle + "投过币了");
            return false;
        }
    }

    /**
     * 检查是否投币
     *
     * @param bvid av号
     * @return 返回是否投过硬币了
     */
    private boolean isCoin(String bvid) {
        String urlParam = "?bvid=" + bvid;
        JsonObject result = HttpUtil.doGet(ApiList.isCoin + urlParam);

        int multiply = result.getAsJsonObject("data").get("multiply").getAsInt();
        if (multiply > 0) {
            logger.info("之前已经为av" + bvid + "投过" + multiply + "枚硬币啦");
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getName() {
        return taskName;
    }
}
