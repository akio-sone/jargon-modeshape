/**
 * 
 */
package org.irods.jargon.modeshape.connector;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;

import javax.jcr.NamespaceRegistry;
import javax.jcr.RepositoryException;

import org.infinispan.schematic.document.Document;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonRuntimeException;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.IRODSFileSystemSingletonWrapper;
import org.irods.jargon.modeshape.connector.exceptions.UnknownNodeTypeException;
import org.irods.jargon.modeshape.connector.nodetypes.NodeTypeFactory;
import org.irods.jargon.modeshape.connector.nodetypes.NodeTypeFactoryImpl;
import org.modeshape.jcr.api.nodetype.NodeTypeManager;
import org.modeshape.jcr.cache.DocumentStoreException;
import org.modeshape.jcr.spi.federation.DocumentChanges;
import org.modeshape.jcr.spi.federation.DocumentWriter;
import org.modeshape.jcr.spi.federation.PageKey;
import org.modeshape.jcr.spi.federation.Pageable;
import org.modeshape.jcr.spi.federation.WritableConnector;
import org.modeshape.jcr.value.Name;
import org.modeshape.jcr.value.ValueFactories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mike Conway - DICE ref
 *         https://github.com/ModeShape/modeshape/tree/master
 *         /connectors/modeshape
 *         -connector-git/src/main/java/org/modeshape/connector/git
 */
public class IrodsWriteableConnector extends WritableConnector implements
		Pageable {

	/**
	 * The string path for a {@link File} object that represents the top-level
	 * directory accessed by this connector. This is set via reflection and is
	 * required for this connector.
	 */
	private String directoryPath;

	public static final Logger log = LoggerFactory
			.getLogger(IrodsWriteableConnector.class);

	/**
	 * A boolean flag that specifies whether this connector should add the
	 * 'mix:mimeType' mixin to the 'nt:resource' nodes to include the
	 * 'jcr:mimeType' property. If set to <code>true</code>, the MIME type is
	 * computed immediately when the 'nt:resource' node is accessed, which might
	 * be expensive for larger files. This is <code>false</code> by default.
	 */
	private boolean addMimeTypeMixin = false;

	/**
	 * The regular expression that, if matched by a file or folder, indicates
	 * that the file or folder should be included.
	 */
	private String inclusionPattern;

	/**
	 * The regular expression that, if matched by a file or folder, indicates
	 * that the file or folder should be ignored.
	 */
	private String exclusionPattern;

	/**
	 * Configuration flag that can cause this connector to operare in read-only
	 * mode if desired
	 */
	private boolean readOnly = false;

	/**
	 * Configuration flag that can cause AVU nodes to be added when creating or
	 * reading documents
	 */
	private boolean addAvus = false;

	/**
	 * The {@link FilenameFilter} implementation that is instantiated in the
	 * {@link #initialize(NamespaceRegistry, NodeTypeManager)} method.
	 */
	private InclusionExclusionFilenameFilter filenameFilter;

	/**
	 * Created during init phase, this will handle all the node type detection
	 * and path munging for ids
	 */
	private PathUtilities pathUtilities;

	/**
	 * Created during the init phase, this factory will produce node creators
	 * based on the JCR types in the given ids
	 */

	private NodeTypeFactory nodeTypeFactory;

	/**
	 * The maximum number of children a folder will expose at any given time.
	 */
	public static final int PAGE_SIZE = 5000;

	@Override
	public Document getDocumentById(String id) {

		try {
			log.info("getDocumentById()");

			if (id == null || id.isEmpty()) {
				throw new IllegalArgumentException("null or empty id");
			}

			log.info("id:{}", id);

			try {
				return nodeTypeFactory.instanceForId(id, 0).getDocument(id);
			} catch (UnknownNodeTypeException e) {
				log.error("unknown node type for id:{}", id, e);
				throw new DocumentStoreException(id, e);
			} catch (JargonException e) {
				log.error("jargon exception getting  node type for id:{}", id,
						e);
				throw new DocumentStoreException(id, e);
			}

		} finally {
			this.instanceIrodsFileSystem().closeAndEatExceptions();
		}

	}

	@Override
	public String getDocumentId(String arg0) {
		return null;
	}

	@Override
	public Collection<String> getDocumentPathsById(String arg0) {
		return null;
	}

	@Override
	public boolean hasDocument(String arg0) {
		return false;
	}

	@Override
	public String newDocumentId(String arg0, Name arg1, Name arg2) {
		return null;
	}

	@Override
	public boolean removeDocument(String arg0) {
		return false;
	}

	@Override
	public void storeDocument(Document arg0) {

	}

	@Override
	public void updateDocument(DocumentChanges arg0) {

	}

	/**
	 * @return the addMimeTypeMixin
	 */
	public boolean isAddMimeTypeMixin() {
		return addMimeTypeMixin;
	}

	/**
	 * @param addMimeTypeMixin
	 *            the addMimeTypeMixin to set
	 */
	public void setAddMimeTypeMixin(boolean addMimeTypeMixin) {
		this.addMimeTypeMixin = addMimeTypeMixin;
	}

	/**
	 * @return the inclusionPattern
	 */
	public String getInclusionPattern() {
		return inclusionPattern;
	}

	/**
	 * @param inclusionPattern
	 *            the inclusionPattern to set
	 */
	public void setInclusionPattern(String inclusionPattern) {
		this.inclusionPattern = inclusionPattern;
	}

	/**
	 * @return the exclusionPattern
	 */
	public String getExclusionPattern() {
		return exclusionPattern;
	}

	/**
	 * @param exclusionPattern
	 *            the exclusionPattern to set
	 */
	public void setExclusionPattern(String exclusionPattern) {
		this.exclusionPattern = exclusionPattern;
	}

	/**
	 * @return the readOnly
	 */
	public boolean isReadOnly() {
		return readOnly;
	}

	/**
	 * @param readOnly
	 *            the readOnly to set
	 */
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.modeshape.jcr.spi.federation.Connector#initialize(javax.jcr.
	 * NamespaceRegistry, org.modeshape.jcr.api.nodetype.NodeTypeManager)
	 */
	@Override
	public void initialize(NamespaceRegistry registry,
			NodeTypeManager nodeTypeManager) throws RepositoryException,
			IOException {
		try {
			super.initialize(registry, nodeTypeManager);

			checkFieldNotNull(directoryPath, "directoryPath");

			// Initialize the filename filter ...
			filenameFilter = new InclusionExclusionFilenameFilter();
			if (exclusionPattern != null)
				filenameFilter.setExclusionPattern(exclusionPattern);
			if (inclusionPattern != null)
				filenameFilter.setInclusionPattern(inclusionPattern);

			this.pathUtilities = new PathUtilities(directoryPath,
					filenameFilter);

			try {
				this.nodeTypeFactory = new NodeTypeFactoryImpl(this
						.instanceIrodsFileSystem()
						.getIRODSAccessObjectFactory(), getIrodsAccount(), this);
			} catch (JargonException e) {
				log.error("error creating NodeTypeFactory", e);
				throw new RepositoryException(
						"jargon error creating factory for creating nodes", e);
			}

			log.info("initialized");
		} finally {
			this.instanceIrodsFileSystem().closeAndEatExceptions();
		}
	}

	/**
	 * FIXME: shim for authentication
	 * 
	 * @return
	 */
	private IRODSAccount getIrodsAccount() {
		try {

			IRODSAccount irodsAccount = IRODSAccount.instance(
					"consortium.local", 1247, "test1", "test", "", "test1", "");
			return irodsAccount;

		} catch (JargonException e) {
			throw new JargonRuntimeException("unable to create irodsAccount", e);
		}
	}

	/**
	 * @return the directoryPath
	 */
	public String getDirectoryPath() {
		return directoryPath;
	}

	/**
	 * @param directoryPath
	 *            the directoryPath to set
	 */
	public void setDirectoryPath(String directoryPath) {
		this.directoryPath = directoryPath;
	}

	public PathUtilities getPathUtilities() {
		return pathUtilities;
	}

	/**
	 * Wraps the <code>newDocument()</code> method to allow factories to create
	 * new documents
	 * 
	 * @param id
	 *            <code>String</code> with the document id
	 * @return {@link DocumentWriter}
	 */
	public DocumentWriter createNewDocumentForId(final String id) {
		try {
			log.info("createNewDocumentForId()");
			if (id == null || id.isEmpty()) {
				throw new IllegalArgumentException("null or empty id");
			}

			return this.newDocument(id);
		} finally {
			this.instanceIrodsFileSystem().closeAndEatExceptions();
		}
	}

	/**
	 * Allow objects to get a handle to factories for various types
	 * 
	 * @return {@link ValueFactories} as obtained in the
	 *         <code>factories()</code> method of a connector
	 */
	public ValueFactories obtainHandleToFactories() {
		return this.factories();
	}

	public boolean isAddAvus() {
		return addAvus;
	}

	public void setAddAvus(boolean addAvus) {
		this.addAvus = addAvus;
	}

	/**
	 * Get a reference to the IRODSFileSystem that will be a singleton
	 * 
	 * @return
	 */
	public IRODSFileSystem instanceIrodsFileSystem() {
		return IRODSFileSystemSingletonWrapper.instance();
	}

	@Override
	public Document getChildren(PageKey pageKey) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @return the nodeTypeFactory
	 */
	public NodeTypeFactory getNodeTypeFactory() {
		return nodeTypeFactory;
	}

}
