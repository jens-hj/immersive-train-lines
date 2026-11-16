package dev.jensh.immersivetrainlines.map;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.jensh.immersivetrainlines.raster.StationMark;
import dev.jensh.immersivetrainlines.raster.TrackPath;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads the track graph Create keeps in sync on every client. */
public record RailNetwork(List<TrackPath> paths, List<StationMark> stations) {
	public static final RailNetwork EMPTY = new RailNetwork(List.of(), List.of());

	public static int version() {
		return CreateClient.RAILWAYS.version;
	}

	public static RailNetwork read(ResourceKey<Level> dimension) {
		List<TrackPath> paths = new ArrayList<>();
		List<StationMark> stations = new ArrayList<>();
		for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
			for (TrackNodeLocation location : graph.getNodes()) {
				if (location.dimension != dimension) continue;
				TrackNode node = graph.locateNode(location);
				if (node == null) continue;
				for (Map.Entry<TrackNode, TrackEdge> connection : graph.getConnectionsFrom(node).entrySet()) {
					TrackEdge edge = connection.getValue();
					// Every edge is stored in both directions; draw it once.
					if (edge.isInterDimensional() || connection.getKey().hashCode() > node.hashCode()) continue;
					paths.add(sample(graph, edge));
				}
			}
			for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
				Couple<TrackNodeLocation> ends = station.edgeLocation;
				if (ends == null || ends.getFirst().dimension != dimension) continue;
				TrackNode first = graph.locateNode(ends.getFirst());
				TrackNode second = graph.locateNode(ends.getSecond());
				if (first == null || second == null) continue;
				TrackEdge edge = graph.getConnection(Couple.create(first, second));
				if (edge == null || edge.getLength() <= 0) continue;
				Vec3 position = edge.getPosition(graph, station.getLocationOn(edge) / edge.getLength());
				stations.add(new StationMark(station.name, position.x, position.y, position.z));
			}
		}
		return new RailNetwork(paths, stations);
	}

	private static TrackPath sample(TrackGraph graph, TrackEdge edge) {
		int steps = Math.max(1, (int) Math.ceil(edge.getLength() * 2));
		double[] x = new double[steps + 1];
		double[] y = new double[steps + 1];
		double[] z = new double[steps + 1];
		for (int i = 0; i <= steps; i++) {
			Vec3 position = edge.getPosition(graph, i / (double) steps);
			x[i] = position.x;
			y[i] = position.y;
			z[i] = position.z;
		}
		return new TrackPath(x, y, z);
	}
}
