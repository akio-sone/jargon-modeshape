/**
 * 
 */
package org.irods.jargon.modeshape.connector.nodetypes;

import javax.jcr.RepositoryException;

import org.infinispan.schematic.document.Document;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.service.AbstractJargonService;
import org.irods.jargon.modeshape.connector.IrodsNodeTypes;
import org.irods.jargon.modeshape.connector.IrodsWriteableConnector;
import org.irods.jargon.modeshape.connector.PathUtilities;
import org.irods.jargon.modeshape.connector.exceptions.UnknownNodeTypeException;
import org.modeshape.jcr.spi.federation.DocumentChanges;
import org.modeshape.jcr.spi.federation.DocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for node types
 * 
 * @author Mike Conway - DICE
 * 
 */
public class NodeTypeFactoryImpl extends AbstractJargonService implements
		NodeTypeFactory {

	private final IrodsWriteableConnector irodsWriteableConnector;

	public static final Logger log = LoggerFactory
			.getLogger(NodeTypeFactoryImpl.class);

	/**
	 * Constructor for factory to create node types as described by the id
	 * 
	 * @param irodsAccessObjectFactory
	 *            {@link IRODSAccessObjectFactory}
	 * @param irodsAccount
	 *            {@link IRODSAccount}
	 * @param pathUtilities
	 *            {@link PathUtilities}
	 */
	public NodeTypeFactoryImpl(
			final IRODSAccessObjectFactory irodsAccessObjectFactory,
			final IRODSAccount irodsAccount,
			final IrodsWriteableConnector irodsWriteableConnector) {
		super(irodsAccessObjectFactory, irodsAccount);

		if (irodsWriteableConnector == null) {
			throw new IllegalArgumentException("null irodsWriteableConnector");
		}
		this.irodsWriteableConnector = irodsWriteableConnector;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.irods.jargon.modeshape.connector.nodetypes.NodeTypeFactory#instanceForId
	 * (java.lang.String, int)
	 */
	@Override
	public Document instanceForId(final String id, final int offset)
			throws RepositoryException, UnknownNodeTypeException {
		log.info("instanceForId()");

		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("id is null or missing");
		}

		log.info("getting creator for node type based on id..");
		AbstractNodeTypeCreator creator = instanceNodeTypeCreatorForId(id);
		log.info("got creator, get instance of document...");
		return creator.instanceForId(id, offset);

	}

	private AbstractNodeTypeCreator instanceNodeTypeCreatorForId(final String id)
			throws UnknownNodeTypeException {

		log.info("instanceNodeTypeCreatorForId()");

		if (id == null || id.isEmpty()) {
			throw new IllegalArgumentException("id is null or missing");
		}

		IrodsNodeTypes irodsNodeType = irodsWriteableConnector
				.getPathUtilities().getNodeTypeForId(id);
		log.info("resolved node type:{}", irodsNodeType);

		switch (irodsNodeType) {
		case ROOT_NODE:
			return new FileNodeCreator(irodsAccessObjectFactory, irodsAccount,
					irodsWriteableConnector);
		case RESOURCE_NODE:
			throw new UnsupportedOperationException("blah");
		case CONTENT_NODE:
			return new ContentNodeCreator(irodsAccessObjectFactory,
					irodsAccount, irodsWriteableConnector);
		case AVU_NODE:
			throw new UnsupportedOperationException("blah");
		default:
			return new FileNodeCreator(irodsAccessObjectFactory, irodsAccount,
					irodsWriteableConnector);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.modeshape.connector.nodetypes.NodeTypeFactory#
	 * instanceCreatorForDocument(org.infinispan.schematic.document.Document)
	 */
	@Override
	public AbstractNodeTypeCreator instanceCreatorForDocument(
			final Document document) throws UnknownNodeTypeException,
			RepositoryException {

		log.info("instanceCreatorForDocument()");

		if (document == null) {
			throw new IllegalArgumentException("document is null");
		}

		DocumentReader documentReader = irodsWriteableConnector
				.produceDocumentReaderFromDocument(document);

		String primaryType = documentReader.getPrimaryTypeName();
		log.info("primaryType:{}", primaryType);

		if (PathUtilities.NT_FILE.equals(primaryType)) {
			return new FileNodeCreator(irodsAccessObjectFactory, irodsAccount,
					irodsWriteableConnector);
		} else if (PathUtilities.NT_FOLDER.equals(primaryType)) {
			return new FileNodeCreator(irodsAccessObjectFactory, irodsAccount,
					irodsWriteableConnector);
		} else if (PathUtilities.NT_RESOURCE.equals(primaryType)) {
			return new ContentNodeCreator(irodsAccessObjectFactory,
					irodsAccount, irodsWriteableConnector);
		} else {
			log.error("cannot create a node creator for a given node type:{}",
					primaryType);
			throw new UnknownNodeTypeException(
					"cannot create node creator for given node type:"
							+ primaryType);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.irods.jargon.modeshape.connector.nodetypes.NodeTypeFactory#
	 * instanceCreatorForDocumentChanges
	 * (org.modeshape.jcr.spi.federation.DocumentChanges)
	 */
	@Override
	public AbstractNodeTypeCreator instanceCreatorForDocumentChanges(
			final DocumentChanges documentChanges)
			throws UnknownNodeTypeException, RepositoryException {

		log.info("instanceCreatorForDocumentChanges()");

		if (documentChanges == null) {
			throw new IllegalArgumentException("documentChanges is null");
		}

		Document document = documentChanges.getDocument();
		return instanceCreatorForDocument(document);
	}

}
