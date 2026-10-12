-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: order_db
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `order_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `order_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `order_db`;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `item_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint unsigned NOT NULL,
  `product_id` bigint NOT NULL,
  `product_code_snapshot` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name_snapshot` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unit_price_snapshot` decimal(10,2) NOT NULL,
  `quantity` int unsigned NOT NULL,
  `line_amount` decimal(12,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`item_id`),
  KEY `idx_order_items_order_id` (`order_id`),
  KEY `idx_order_items_product_id` (`product_id`),
  CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_order_items_line_amount_nonnegative` CHECK ((`line_amount` >= 0)),
  CONSTRAINT `chk_order_items_quantity_positive` CHECK ((`quantity` > 0)),
  CONSTRAINT `chk_order_items_unit_price_nonnegative` CHECK ((`unit_price_snapshot` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_items`
--

LOCK TABLES `order_items` WRITE;
/*!40000 ALTER TABLE `order_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `window_id` bigint NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pickup_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin DEFAULT NULL,
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  UNIQUE KEY `uk_orders_user_idempotency_key` (`user_id`,`idempotency_key`),
  KEY `idx_orders_user_created_at` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'order_db'
--

--
-- Current Database: `user_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `user_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `user_db`;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CUSTOMER',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `balance` decimal(10,2) NOT NULL DEFAULT '0.00',
  `password_hash` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'user_db'
--

--
-- Current Database: `product_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `product_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `product_db`;

--
-- Table structure for table `dining_windows`
--

DROP TABLE IF EXISTS `dining_windows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dining_windows` (
  `window_id` bigint NOT NULL AUTO_INCREMENT,
  `window_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`window_id`),
  UNIQUE KEY `uk_dining_windows_code` (`window_code`),
  CONSTRAINT `chk_dining_windows_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dining_windows`
--

LOCK TABLES `dining_windows` WRITE;
/*!40000 ALTER TABLE `dining_windows` DISABLE KEYS */;
INSERT INTO `dining_windows` VALUES (1,'W-DEMO-01','ä¸€æ¥¼æµ‹è¯•çª—å£','ä¸€æ¥¼ä¸œä¾§',1,'2026-10-12 03:55:35','2026-10-12 03:55:35');
/*!40000 ALTER TABLE `dining_windows` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_categories`
--

DROP TABLE IF EXISTS `product_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_categories` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `window_id` bigint NOT NULL,
  `category_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_product_categories_window_name` (`window_id`,`category_name`),
  UNIQUE KEY `uk_product_categories_window_category` (`window_id`,`category_id`),
  KEY `idx_product_categories_window_status_sort` (`window_id`,`status`,`sort_order`),
  CONSTRAINT `fk_product_categories_window` FOREIGN KEY (`window_id`) REFERENCES `dining_windows` (`window_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_product_categories_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_categories`
--

LOCK TABLES `product_categories` WRITE;
/*!40000 ALTER TABLE `product_categories` DISABLE KEYS */;
INSERT INTO `product_categories` VALUES (1,1,'ä¸»é£Ÿ',10,1,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(2,1,'é¥®å“',20,1,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(3,1,'åœç”¨åˆ†ç±»ç¤ºä¾‹',30,0,'2026-10-12 03:55:35','2026-10-12 03:55:35');
/*!40000 ALTER TABLE `product_categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `window_id` bigint NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `sale_status` tinyint unsigned NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_products_window_code` (`window_id`,`product_code`),
  KEY `idx_products_window_sale_sort` (`window_id`,`sale_status`,`sort_order`),
  KEY `idx_products_window_category_sale_sort` (`window_id`,`category_id`,`sale_status`,`sort_order`),
  CONSTRAINT `fk_products_window` FOREIGN KEY (`window_id`) REFERENCES `dining_windows` (`window_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_products_window_category` FOREIGN KEY (`window_id`, `category_id`) REFERENCES `product_categories` (`window_id`, `category_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_products_price_nonnegative` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_products_sale_status` CHECK ((`sale_status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,1,1,'DEMO-RICE-01','é¸¡è‚‰é¥­','å¼€å‘çŽ¯å¢ƒç¤ºä¾‹èœå“',NULL,18.50,1,10,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(2,1,2,'DEMO-DRINK-01','è±†æµ†','å¼€å‘çŽ¯å¢ƒç¤ºä¾‹é¥®å“',NULL,3.00,1,10,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(3,1,NULL,'DEMO-UNCAT-01','ç“¶è£…æ°´','æœªå…³è”åˆ†ç±»çš„ç¤ºä¾‹å•†å“',NULL,2.00,1,20,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(4,1,3,'DEMO-HIDDEN-01','éšè—åˆ†ç±»èœå“','åœç”¨åˆ†ç±»ä¸‹çš„ç¤ºä¾‹å•†å“',NULL,9.00,1,30,'2026-10-12 03:55:35','2026-10-12 03:55:35'),(5,1,1,'DEMO-OFFSALE-01','å·²ä¸‹æž¶ç¤ºä¾‹èœå“','ä¸åº”å‡ºçŽ°åœ¨é¡¾å®¢èœå•ä¸­',NULL,12.00,0,40,'2026-10-12 03:55:35','2026-10-12 03:55:35');
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'product_db'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-12  4:22:06
