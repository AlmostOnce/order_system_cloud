// 仅在全新Mongo数据卷初始化时运行；已存在数据卷需由部署人员显式执行迁移。
const user = process.env.COMMENT_MONGO_USERNAME || 'comment_service';
const password = process.env.COMMENT_MONGO_PASSWORD;
if (!password) throw new Error('COMMENT_MONGO_PASSWORD must be configured');
const comments = db.getSiblingDB('comment_db');
comments.createUser({user, pwd: password, roles: [{role: 'readWrite', db: 'comment_db'}]});
comments.createCollection('comments');
comments.comments.createIndex({committed: 1, createdAt: 1}, {name: 'pending_orphan_scan'});
