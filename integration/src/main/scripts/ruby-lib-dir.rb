#!/usr/bin/env ruby

# Add all files in testscripts\SlingRuby\lib directory to ruby "require" search path
$LOAD_PATH << File.expand_path(File.dirname(__FILE__) + '/../../../target/nakamura-testscripts/SlingRuby/lib')
