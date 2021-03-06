/*
 * This file is part of l2jserver2 <l2jserver2.com>.
 *
 * l2jserver2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * l2jserver2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with l2jserver2.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.service.game.template;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.l2jserver.model.id.TemplateID;
import com.l2jserver.model.template.CharacterTemplate;
import com.l2jserver.model.template.ItemTemplate;
import com.l2jserver.model.template.NPCTemplate;
import com.l2jserver.model.template.SkillTemplate;
import com.l2jserver.model.template.Teleports;
import com.l2jserver.model.template.Template;
import com.l2jserver.service.AbstractConfigurableService;
import com.l2jserver.service.AbstractService.Depends;
import com.l2jserver.service.ServiceStartException;
import com.l2jserver.service.ServiceStopException;
import com.l2jserver.service.cache.Cache;
import com.l2jserver.service.cache.CacheService;
import com.l2jserver.service.configuration.ConfigurationService;
import com.l2jserver.service.core.logging.LoggingService;
import com.l2jserver.service.core.vfs.VFSService;
import com.l2jserver.util.factory.CollectionFactory;
import com.l2jserver.util.jaxb.CharacterTemplateIDAdapter;
import com.l2jserver.util.jaxb.ItemTemplateIDAdapter;
import com.l2jserver.util.jaxb.NPCTemplateIDAdapter;
import com.l2jserver.util.jaxb.SkillTemplateIDAdapter;
import com.l2jserver.util.jaxb.TeleportationTemplateIDAdapter;

/**
 * This service loads template data from XML files using the {@link JAXB}
 * service.
 * 
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 */
@Depends({ LoggingService.class, VFSService.class, CacheService.class,
		ConfigurationService.class })
public class XMLTemplateService extends
		AbstractConfigurableService<XMLTemplateServiceConfiguration> implements
		TemplateService {
	/**
	 * The logger
	 */
	private final Logger log = LoggerFactory.getLogger(this.getClass());

	/**
	 * The vfs service
	 */
	private final VFSService vfsService;
	/**
	 * The cache service
	 */
	private final CacheService cacheService;

	/**
	 * The npc template id adapter
	 */
	private final NPCTemplateIDAdapter npcTemplateIdAdapter;
	/**
	 * The item template id adapter
	 */
	private final ItemTemplateIDAdapter itemTemplateIdAdapter;
	/**
	 * The skill template id adapter
	 */
	private final SkillTemplateIDAdapter skillTemplateIdAdapter;
	/**
	 * The character template id adapter
	 */
	private final CharacterTemplateIDAdapter charIdTemplateAdapter;
	/**
	 * The teleportation template id adapter
	 */
	private final TeleportationTemplateIDAdapter teleportationIdTemplateAdapter;

	/**
	 * The {@link JAXB} context
	 */
	private JAXBContext context;
	/**
	 * The {@link JAXB} unmarshaller
	 */
	private Unmarshaller unmarshaller;

	/**
	 * An cache of all loaded templates
	 */
	@SuppressWarnings("rawtypes")
	private Cache<TemplateID, Template> templates;

	/**
	 * @param vfsService
	 *            the vfs service
	 * @param cacheService
	 *            the cache servicef
	 * @param npcTemplateIdAdapter
	 *            the npc template id adapter
	 * @param itemTemplateIdAdapter
	 *            the item template id adapter
	 * @param skillTemplateIdAdapter
	 *            the skill template id adapter
	 * @param charIdTemplateAdapter
	 *            the character id template adapter
	 * @param teleportationIdTemplateAdapter
	 *            the teleportation template id adapter
	 */
	@Inject
	public XMLTemplateService(final VFSService vfsService,
			CacheService cacheService,
			NPCTemplateIDAdapter npcTemplateIdAdapter,
			ItemTemplateIDAdapter itemTemplateIdAdapter,
			SkillTemplateIDAdapter skillTemplateIdAdapter,
			CharacterTemplateIDAdapter charIdTemplateAdapter,
			TeleportationTemplateIDAdapter teleportationIdTemplateAdapter) {
		super(XMLTemplateServiceConfiguration.class);
		this.vfsService = vfsService;
		this.cacheService = cacheService;
		this.npcTemplateIdAdapter = npcTemplateIdAdapter;
		this.itemTemplateIdAdapter = itemTemplateIdAdapter;
		this.skillTemplateIdAdapter = skillTemplateIdAdapter;
		this.charIdTemplateAdapter = charIdTemplateAdapter;
		this.teleportationIdTemplateAdapter = teleportationIdTemplateAdapter;
	}

	@Override
	protected void doStart() throws ServiceStartException {
		templates = cacheService.createEternalCache("templates", 100 * 1000);
		try {
			log.debug("Creating JAXBContext instance");
			context = JAXBContext.newInstance(CharacterTemplate.class,
					NPCTemplate.class, ItemTemplate.class, SkillTemplate.class,
					Teleports.class);

			log.debug("Creating Unmarshaller instance");
			unmarshaller = context.createUnmarshaller();
			final Path templatePath = vfsService.resolveDataFile(config
					.getTemplateDirectory());

			log.info("Scanning {} for XML templates", templatePath);

			final List<Source> schemas = CollectionFactory.newList();

			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("l2jserver2.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("item.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("skill.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("character.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("npc.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("teleport.xsd")))));
			schemas.add(new StreamSource(new ByteArrayInputStream(Files
					.readAllBytes(templatePath.resolve("zones.xsd")))));

			final List<Path> templateList = CollectionFactory.newList();
			final boolean includeSchemas = config.isSchemaValidationEnabled();
			Files.walkFileTree(templatePath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					final String name = file.getFileName().toString();
					if (name.endsWith(".xml")) {
						if (name.endsWith("zones.xml"))
							return FileVisitResult.CONTINUE;
						templateList.add(file);
					}
					return FileVisitResult.CONTINUE;
				}
			});
			log.info("Found {} XML templates", templateList.size());
			if (includeSchemas) {
				unmarshaller.setSchema(SchemaFactory.newInstance(
						XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(
						schemas.toArray(new Source[schemas.size()])));
			} else {
				log.warn("Template schema validation is disabled. This is not recommended for live servers.");
			}

			unmarshaller.setAdapter(npcTemplateIdAdapter);
			unmarshaller.setAdapter(itemTemplateIdAdapter);
			unmarshaller.setAdapter(skillTemplateIdAdapter);
			unmarshaller.setAdapter(charIdTemplateAdapter);
			unmarshaller.setAdapter(teleportationIdTemplateAdapter);

			for (final Path path : templateList) {
				loadTemplate(path);
			}
		} catch (JAXBException e) {
			throw new ServiceStartException(e);
		} catch (IOException e) {
			throw new ServiceStartException(e);
		} catch (SAXException e) {
			throw new ServiceStartException(e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Template> T getTemplate(TemplateID<T, ?> id) {
		Preconditions.checkNotNull(id, "id");
		return (T) templates.get(id);
	}

	/**
	 * Loads the template located in <tt>path</tt>
	 * 
	 * @param path
	 *            the path to the template
	 * @throws JAXBException
	 *             if any error occur while processing the XML
	 * @throws IOException
	 *             if any error occur in the I/O level
	 * @throws ServiceStartException
	 *             if the template type is not known
	 */
	public void loadTemplate(Path path) throws JAXBException, IOException,
			ServiceStartException {
		Preconditions.checkNotNull(path, "path");
		log.debug("Loading template {}", path);
		final InputStream in = Files.newInputStream(path,
				StandardOpenOption.READ);
		try {
			Object obj = unmarshaller.unmarshal(in);
			if (obj instanceof Template) {
				final Template template = (Template) obj;
				log.debug("Template loaded: {}", template);
				if (template.getID() != null)
					templates.put(template.getID(), template);
			} else if (obj instanceof Teleports) {
				for (final Template template : ((Teleports) obj).getTeleport()) {
					log.debug("Template loaded: {}", template);
					if (template.getID() != null)
						templates.put(template.getID(), template);
				}
			} else {
				throw new ServiceStartException(
						"Unknown template container type: " + obj);
			}
		} finally {
			// in.close();
		}
	}

	/**
	 * Removes the given <tt>template</tt> from the cache
	 * 
	 * @param template
	 *            the template to be purged
	 */
	public void removeTemplate(Template template) {
		Preconditions.checkNotNull(template, "template");
		log.debug("Removing template {}", template);
		templates.remove(template.getID());
	}

	@Override
	protected void doStop() throws ServiceStopException {
		cacheService.dispose(templates);
		templates = null;
		unmarshaller = null;
		context = null;
	}
}
