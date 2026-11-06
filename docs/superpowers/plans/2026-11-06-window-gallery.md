# 店铺最多五张展示图片实施计划

**Goal:** 每个店铺保存0至5张有序图片，第一张为首页封面，点餐页手动滑动并点击预览。

**Architecture:** 复用文件服务的WINDOW_IMAGE文件键及归属校验；窗口新增JSON图片键数组，原封面字段同步保存第一张。一次提交完整列表，数据库限制数组长度，避免并发追加突破数量上限。旧数据回填原图，不删除上传文件。

**Tech Stack:** Spring、MyBatis-Plus、MySQL JSON、uni-app x原生swiper。

用户已确认上述产品范围。管理端仓库未知，交付管理接口，不向学生端添加商家操作。

- [x] 增加数量、重复键、归属和旧封面兼容的测试；Java测试只写不运行。
- [x] 更新窗口DO、DTO、管理接口、菜单返回与缓存版本；保留旧单图接口并同步数据。
- [x] 提供V11迁移：回填旧图、限制JSON数组最多5个元素；不擅自执行迁移。
- [x] 学生点餐页复用现有112px图片高度，手动滑动；多图显示页码，无自动播放。
- [x] 前端回归、源码静态检查、接口快照和设计约定更新，记录未验证边界。

Ruling: 保留旧封面列以兼容首页及搜索读取，所有窗口图片写入入口同步封面与列表；完整替换并发时最后提交生效，数量不会累加超限。

不运行Maven，不启动服务、不部署、不提交或推送；迁移已按用户后续要求执行；既有工作区变更保持原状。

## 验证与审查记录

- Node新回归先失败于缺少windowGalleryUrls，实现后通过；覆盖旧封面、显式清空、最多五张、去重、整组预览及越界事件。
- 七项前端检查通过：window-gallery、vapor、menu-linkage、images、search-experience、comments、pull-refresh；Vue编译与组件规则检查不等于Android真机验收。
- 实际V11仅在MySQL连接内临时表执行：旧封面/空封面回填正确，允许五张，六张与非数组被CHECK拒绝。未迁移业务表。
- 独立只读审查发现普通资料更新无条件回写图片，已修复为仅实际提交图片字段时更新两列，并补充Java回归源码。
- 审查发现已有图提升封面时实现与测试不符，已统一为只调整顺序并保留原封面；新图仍替换首图，空串移除首图。
- 13份Java源码括号及两份OpenAPI引用检查通过；未编译或执行Java测试。前后端diff --check通过。

## 接口与发布

PUT /api/products/admin/windows/{windowId}/images，JSON请求体为fileKeys字符串数组。上传沿用文件服务，bizType=WINDOW_IMAGE、bizRef=windowId。响应同时返回imageFileKeys及imageUrls。空数组清空；完整列表决定展示顺序，第一张为首页封面。

先执行product_db/V11迁移，再更新商品服务，最后更新学生端；避免旧商品服务继续写旧单图列。菜单缓存前缀升为v3，旧缓存自然过期。商家Vue管理页未接入，uni-app仅提供学生展示。

## 2026-11-06业务库迁移结果

用户明确要求确认无误的迁移默认直接执行。已先备份order_db.orders及product_db.dining_windows，备份与结果记录：`C:/Users/DELL/.codex/backups/order-system-cloud/20261106-101122`。

- 已执行order_db/V6，两个索引列顺序核验为user_id,order_id及user_id,status,order_id。
- 已执行product_db/V11，9个窗口均完成图片数组回填，未回填、非法数组及封面不一致计数均为0；CHECK约束ENFORCED=YES。
- 订单SELECT全部字段迁移前后逐行一致，窗口原封面逐行一致，未删除业务记录或文件。
- 实际小样本EXPLAIN（user_id=1）全部/待支付仍选用户幂等键并filesort；不强制索引，不据此声称完成大规模性能验收。
- 未运行Maven、Java测试或服务重启，未部署学生端；商品服务加载新代码后才能使用多图接口，避免旧版本继续单列写入。
