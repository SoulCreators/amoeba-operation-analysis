-- V024 rollback 会删除配置导入问题及其历史记录，执行前必须完成备份。
DROP TABLE IF EXISTS flow.configuration_import_issue;
