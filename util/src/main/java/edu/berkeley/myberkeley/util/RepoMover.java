package edu.berkeley.myberkeley.util;

import java.io.File;
import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.core.RepositoryCopier;

public class RepoMover {

	/**
	 * @param args
	 * arg[0] is absolute path to source Jackrabbit Repository e.g. /home/myberkeley/myberkeley/working/sling/jackrabbit
	 * arg[1] is absolute path to destination (new repository)
	 */
	public static void main(String[] args) {
		try {
			RepositoryCopier.copy(new File(args[0]), new File(args[1]));
		} catch (RepositoryException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
