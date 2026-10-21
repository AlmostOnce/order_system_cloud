-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: product_db
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
  `restaurant_id` bigint NOT NULL DEFAULT '1',
  `window_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `is_open` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`window_id`),
  UNIQUE KEY `uk_dining_windows_code` (`window_code`),
  KEY `idx_dining_windows_restaurant` (`restaurant_id`),
  CONSTRAINT `fk_dining_windows_restaurant` FOREIGN KEY (`restaurant_id`) REFERENCES `restaurants` (`restaurant_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `chk_dining_windows_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dining_windows`
--

LOCK TABLES `dining_windows` WRITE;
/*!40000 ALTER TABLE `dining_windows` DISABLE KEYS */;
INSERT INTO `dining_windows` VALUES (1,1,'W-DEMO-01','一楼测试窗口','一楼东侧',NULL,1,1,'2026-10-12 03:55:35','2026-10-14 09:02:14');
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
  `category_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
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
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
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
-- Table structure for table `restaurants`
--

DROP TABLE IF EXISTS `restaurants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `restaurants` (
  `restaurant_id` bigint NOT NULL AUTO_INCREMENT,
  `restaurant_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `restaurant_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `location` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notice` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `status` tinyint unsigned NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`restaurant_id`),
  UNIQUE KEY `uk_restaurants_code` (`restaurant_code`),
  CONSTRAINT `chk_restaurants_status` CHECK ((`status` in (0,1)))
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `restaurants`
--

LOCK TABLES `restaurants` WRITE;
/*!40000 ALTER TABLE `restaurants` DISABLE KEYS */;
INSERT INTO `restaurants` VALUES (1,'R0001','默认餐厅','',NULL,'','',0,1,'2026-10-13 04:25:41','2026-10-13 09:01:40');
/*!40000 ALTER TABLE `restaurants` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-21  6:16:29
