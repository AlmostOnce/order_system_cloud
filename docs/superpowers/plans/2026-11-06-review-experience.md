# 评价展示与一星/五星筛选实施计划

## 已确认范围

- 页面增加菜品图片、所属店铺、真实评分概览和分类数量；保留全部评价，筛选显示全部、好评、差评、有图；好评查询五星，差评查询一星，有图不限评分。
- 发布评价仍支持1～5星；本次不改变既有评分、历史评价或排行榜口径。
- 不新增中评等筛选，不虚构评分分布、好评率或关键词统计。

## 实施与验证

- [x] Comment/WindowReview的Controller、Service、Mapper同步增加可选score分页参数，仅允许1/5；游标绑定用户/目标/分数范围，查询limit+1。
- [x] 新增ReviewSummaryVO及两个summary查询接口，同一SQL统计全部数量、平均分、一星/五星及有图数量，不使用当前页推算。
- [x] 编写V3覆盖索引迁移及真实Mapper临时表SQL检查，备份开发库结构后执行幂等迁移并核验。
- [x] 学生端复用商品图片与窗口查询，概览展示图片/店铺及两类计数条，原生view筛选；切换作废迟到响应并重置游标，保留失败和空态。
- [x] 前端回归覆盖接口参数、统计解析、切换/分页/竞态；补后端边界测试源码，不运行Maven或重启服务。
- [x] 更新接口说明与DESIGN，区分静态/SQL/前端验证和需要加载后端新代码的联调。

## 修改边界

既有comment-service Controller/Service/impl/Mapper分层，uni-app的api/comments.uts及pages/review/list.uvue；不将商家功能加入学生端，不修改已发布正文、图片和评分。


## 实施记录

- 用户最终确认界面标签为“全部、好评、差评、有图”，不附加星数字样；好评=5分、差评=1分；全部保留1～5分历史评价，有图包含任意分数。
- score/hasImages参数贯穿两类Controller、Service、Mapper及学生端API；分页查询限制1～50、取limit+1，错误分数在服务层拒绝，游标复核用户/目标/分数/有图条件。
- V3索引迁移已执行并重复核验，备份C:/Users/DELL/.codex/backups/order-system-cloud/20261106-142823-review-filters。
- 有图补充V4迁移已执行。comments.has_images依据Mongo图片引用回填，发布时与MySQL元数据事务写入；window_reviews.has_images为图片数组的存储生成列。2条已发布菜品评价均找到正文并完成标记核验，原数据数量/评分不变。备份C:/Users/DELL/.codex/backups/order-system-cloud/20261106-143306-review-images。
- V4重复执行验证通过，有图索引4个加上评分索引4个已建立。若旧代码运行期间再次新增菜品评价，会留下NULL标记；summary.imageCount返回null，有图查询明确不可用，不能假装没有图片。加载新代码后可再次执行已提供的回填工具。
- 34项真实MySQL临时表SQL检查通过，覆盖全量/一星/五星/有图/组合筛选、游标隔离、数量上限、元数据写入与未知图片标记。业务表未被测试样本污染。
- 前端评论回归、独立筛选竞态回归、搜索体验及33个页面/组件原生规则检查通过。补充后端参数、数量边界和有图投影测试源码；未运行Maven、Java测试、构建或重启。

## 运行验收待办

- [ ] 评论服务加载本次新代码后验证summary及四类筛选HTTP接口，Android真机验证带图分页/切换和四个标签计数。当前运行进程可能仍是旧代码，不将源码/SQL检查冒充完整联调。
- [ ] 若旧进程期间产生新的NULL图片标记，运行tools/migrate-review-image-filter.py的预检与回填，确认全部投影已知。

- 本次真机验收中ADB设备变为offline，HBuilder日志明确资源进入同步队列。尝试reconnect offline后仍离线、mDNS无发现；未清理手机数据或重启ADB服务，四标签最新画面尚未确认。
