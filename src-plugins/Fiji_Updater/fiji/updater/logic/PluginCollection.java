package fiji.updater.logic;

import fiji.updater.logic.PluginObject.Action;
import fiji.updater.logic.PluginObject.Status;

import fiji.updater.util.DependencyAnalyzer;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class PluginCollection extends ArrayList<PluginObject> {
	protected PluginCollection() { }
	protected static PluginCollection instance;
	public static PluginCollection getInstance() {
		if (instance == null)
			instance = new PluginCollection();
		return instance;
	}

	static DependencyAnalyzer dependencyAnalyzer;

	interface Filter {
		boolean matches(PluginObject plugin);
	}

	public static PluginCollection clone(Iterable<PluginObject> iterable) {
		PluginCollection result = new PluginCollection();
		for (PluginObject plugin : iterable)
			result.add(plugin);
		return result;
	}

	public Iterable<PluginObject> toUploadOrRemove() {
		return filter(or(is(Action.UPLOAD), is(Action.REMOVE)));
	}

	public Iterable<PluginObject> toUpload() {
		return filter(is(Action.UPLOAD));
	}

	public Iterable<PluginObject> toUninstall() {
		return filter(is(Action.UNINSTALL));
	}

	public Iterable<PluginObject> toUpdate() {
		return filter(is(Action.UPDATE));
	}

	public Iterable<PluginObject> upToDate() {
		return filter(is(Action.INSTALLED));
	}

	public Iterable<PluginObject> toInstall() {
		return filter(is(Action.INSTALL));
	}

	public Iterable<PluginObject> toInstallOrUpdate() {
		return filter(oneOf(new Action[] {Action.INSTALL,
					Action.UPDATE}));
	}

	public Iterable<PluginObject> notHidden() {
		// TODO: (Util.isDeveloper || plugin.platform == null ||
		// plugin.platform.equals(Util.platform))
		return filter(not(is(Status.OBSOLETE_UNINSTALLED)));
	}

	public Iterable<PluginObject> uninstalled() {
		return filter(is(Status.NOT_INSTALLED));
	}

	public Iterable<PluginObject> installed() {
		return filter(not(oneOf(new Status[] {Status.NOT_FIJI,
						Status.NOT_INSTALLED})));
	}

	public Iterable<PluginObject> locallyModified() {
		return filter(oneOf(new Status[] {Status.MODIFIED,
					Status.OBSOLETE_MODIFIED}));
	}

	public Iterable<PluginObject> fijiPlugins() {
		return filter(not(is(Status.NOT_FIJI)));
	}

	public Iterable<PluginObject> nonFiji() {
		return filter(is(Status.NOT_FIJI));
	}

	public Iterable<PluginObject> shownByDefault() {
		/*
		 * Let's not show the NOT_INSTALLED ones, as the user chose not
		 * to have them.
		 */
		Status[] oneOf = {
			Status.UPDATEABLE, Status.NEW,
			Status.OBSOLETE, Status.OBSOLETE_MODIFIED
		};
		return filter(oneOf(oneOf));
	}


	public Iterable<PluginObject> uploadable() {
		return filter(new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getStatus()
				.isValid(Action.UPLOAD);
			}
		});
	}

	public Iterable<PluginObject> changes() {
		return filter(new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getAction() !=
					plugin.getStatus().getActions()[0];
			}
		});
	}

	public static class FilteredIterator implements Iterator<PluginObject> {
		Filter filter;
		boolean opposite;
		Iterator<PluginObject> iterator;
		PluginObject next;

		FilteredIterator(Filter filter,
				Iterable<PluginObject> plugins) {
			this.filter = filter;
			iterator = plugins.iterator();
			findNext();
		}

		public boolean hasNext() {
			return next != null;
		}

		public PluginObject next() {
			PluginObject plugin = next;
			findNext();
			return plugin;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		protected void findNext() {
			while (iterator.hasNext()) {
				next = iterator.next();
				if (filter.matches(next))
					return;
			}
			next = null;
		}
	}

	public static Iterable<PluginObject> filter(final Filter filter,
			final Iterable<PluginObject> plugins) {
		return new Iterable<PluginObject>() {
			public Iterator<PluginObject> iterator() {
				return new FilteredIterator(filter, plugins);
			}
		};
	}

	public static Iterable<PluginObject> filter(final String search,
			final Iterable<PluginObject> plugins) {
		final String keyword = search.trim().toLowerCase();
		return filter(new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getFilename().trim().toLowerCase()
					.indexOf(keyword) >= 0;
			}
		}, plugins);
	}

	public Filter is(final Action action) {
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getAction() == action;
			}
		};
	}

	public Filter isNoAction() {
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getAction() ==
					plugin.getStatus().getNoAction();
			}
		};
	}

	public Filter oneOf(final Action[] actions) {
		final Set<Action> oneOf = new HashSet<Action>();
		for (Action action : actions)
			oneOf.add(action);
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return oneOf.contains(plugin.getAction());
			}
		};
	}

	public Filter is(final Status status) {
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.getStatus() == status;
			}
		};
	}

	public Filter oneOf(final Status[] states) {
		final Set<Status> oneOf = new HashSet<Status>();
		for (Status status : states)
			oneOf.add(status);
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return oneOf.contains(plugin.getStatus());
			}
		};
	}

	public Filter not(final Filter filter) {
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return !filter.matches(plugin);
			}
		};
	}

	public Filter or(final Filter a, final Filter b) {
		return new Filter() {
			public boolean matches(PluginObject plugin) {
				return a.matches(plugin) || b.matches(plugin);
			}
		};
	}

	public Iterable<PluginObject> filter(final Filter filter) {
		return filter(filter, this);
	}

	public PluginObject getPlugin(String filename) {
		for (PluginObject plugin : this) {
			if (plugin.getFilename().equals(filename))
				return plugin;
		}
		return null;
	}

	public PluginObject getPlugin(String filename, long timestamp) {
		for (PluginObject plugin : this)
			if (plugin.getFilename().equals(filename) &&
					plugin.getTimestamp() == timestamp)
				return plugin;
		return null;
	}

	public PluginObject getPluginFromDigest(String filename, String digest) {
		for (PluginObject plugin : this)
			if (plugin.getFilename().equals(filename) &&
					plugin.getChecksum().equals(digest))
				return plugin;
		return null;
	}

	protected class Dependencies implements Iterator<Dependency> {
		Iterator<String> iterator;
		Dependency current;
		Dependencies(Iterable<String> dependencies) {
			if (dependencies == null)
				return;
			iterator = dependencies.iterator();
			findNext();
		}

		public boolean hasNext() {
			return current != null;
		}

		public Dependency next() {
			Dependency result = current;
			findNext();
			return result;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		protected void findNext() {
			while (iterator.hasNext()) {
				PluginObject plugin =
					getPlugin(iterator.next());
				if (plugin == null)
					continue;
				current = new Dependency(plugin.getFilename(),
					plugin.getTimestamp(), "at-least");
				return;
			}
			current = null;
		}
	}

	public Iterable<Dependency> analyzeDependencies(PluginObject plugin) {
		try {
			if (dependencyAnalyzer == null)
				dependencyAnalyzer = new DependencyAnalyzer();
			final Iterable<String> dependencies = dependencyAnalyzer
				.getDependencies(plugin.getFilename());

			return new Iterable<Dependency>() {
				public Iterator<Dependency> iterator() {
					return new Dependencies(dependencies);
				}
			};
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean has(final Filter filter) {
		for (PluginObject plugin : this)
			if (filter.matches(plugin))
				return true;
		return false;
	}

	public boolean hasChanges() {
		return has(not(isNoAction()));
	}

	public boolean hasUploadOrRemove() {
		return has(oneOf(new Action[] {Action.UPLOAD, Action.REMOVE}));
	}

	public boolean hasForcableUpdates() {
		for (PluginObject plugin : updateable(true))
			if (!plugin.isUpdateable(false))
				return true;
		return false;
	}

	public Iterable<PluginObject> updateable(final boolean evenForcedOnes) {
		return filter(new Filter() {
			public boolean matches(PluginObject plugin) {
				return plugin.isUpdateable(evenForcedOnes);
			}
		});
	}

	public void markForUpdate(boolean evenForcedUpdates) {
		for (PluginObject plugin : updateable(evenForcedUpdates))
			plugin.setAction(plugin.getStatus()
				.isValid(Action.UPDATE) ?
				Action.UPDATE : Action.UNINSTALL);
	}
}
