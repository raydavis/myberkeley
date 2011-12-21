-- Alter tables from 1.0.2 client.MySQL.ddl to 1.1 client.MySQL.5.ddl
--
ALTER TABLE css ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE au_css ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE cn_css ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE ac_css ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE css_b ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE cn_css_b ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE au_css_b ENGINE=InnoDB COLLATE=utf8_unicode_ci;
ALTER TABLE ac_css_b ENGINE=InnoDB COLLATE=utf8_unicode_ci;

-- The USING HASH clause in 1.0.2 was a no-op. It only applies to the NDB storage engines.
-- -  KEY `rowkey` USING HASH (`rid`,`cid`),
-- +  KEY `rowkey`  (`rid`,`cid`),

-- From sparsemapcontent/src/main/resources/org/sakaiproject/nakamura/lite/storage/jdbc/config/upgrades/Issue125-MySQL.sql
--
ALTER TABLE `css_b` MODIFY `b` mediumblob;
ALTER TABLE `cn_css_b` MODIFY `b` mediumblob;
ALTER TABLE `au_css_b` MODIFY `b` mediumblob;
ALTER TABLE `ac_css_b` MODIFY `b` mediumblob;

-- Add new tables
--
CREATE TABLE  `lk_css` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `rid` varchar(32) NOT NULL,
  `cid` varchar(64) NOT NULL,
  `v` varchar(780) NOT NULL,
  PRIMARY KEY  (`id`),
  KEY `rowkey`  (`rid`,`cid`),
  KEY `cid_locate_i` (`v`(255),`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE css_w (
  `rid` varchar(32) NOT NULL,
  primary key(`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE ac_css_w (
  `rid` varchar(32) NOT NULL,
  primary key(`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE au_css_w (
  `rid` varchar(32) NOT NULL,
  primary key(`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE cn_css_w (
  `rid` varchar(32) NOT NULL,
  primary key(`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE lk_css_w (
  `rid` varchar(32) NOT NULL,
  primary key(`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci; 
CREATE TABLE  css_wr (
  `id` INT NOT NULL AUTO_INCREMENT,
  `cf` varchar(32) NOT NULL,
  `cid` varchar(64) NOT NULL,
  `cname` varchar(64) NOT NULL,
  primary key(`id`),
  unique key css_r_cid (`cf`,`cid`),
  unique key css_r_cnam (`cf`,`cname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
CREATE TABLE  `lk_css_b` (
  `rid` varchar(32) NOT NULL,
  `b` mediumblob,
  PRIMARY KEY (`rid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
