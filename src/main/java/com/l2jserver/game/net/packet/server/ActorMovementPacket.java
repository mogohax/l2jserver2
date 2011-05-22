/*
 * This file is part of l2jserver <l2jserver.com>.
 *
 * l2jserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * l2jserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with l2jserver.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.game.net.packet.server;

import org.jboss.netty.buffer.ChannelBuffer;

import com.l2jserver.game.net.Lineage2Connection;
import com.l2jserver.game.net.packet.AbstractServerPacket;
import com.l2jserver.game.net.packet.server.CharacterCreateFailPacket.Reason;
import com.l2jserver.model.world.Actor;
import com.l2jserver.util.dimensional.Coordinate;

/**
 * This packet notifies the client that the chosen character has been
 * successfully selected.
 * 
 * @author <a href="http://www.rogiel.com">Rogiel</a>
 * @see Reason
 */
public class ActorMovementPacket extends AbstractServerPacket {
	/**
	 * The packet OPCODE
	 */
	public static final int OPCODE = 0x2f;

	/**
	 * The selected character
	 */
	private final Actor actor;
	/**
	 * The source target
	 */
	private Coordinate target;

	public ActorMovementPacket(Actor actor, Coordinate target) {
		super(OPCODE);
		this.actor = actor;
		this.target = target;
	}

	@Override
	public void write(Lineage2Connection conn, ChannelBuffer buffer) {
		buffer.writeInt(actor.getID().getID());

		// target
		buffer.writeInt(target.getX());
		buffer.writeInt(target.getY());
		buffer.writeInt(target.getZ());
		
		// source
		buffer.writeInt(actor.getPoint().getX());
		buffer.writeInt(actor.getPoint().getY());
		buffer.writeInt(actor.getPoint().getZ());
	}
}