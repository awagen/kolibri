function idForMetric(metric) {
    let name = metric.type
    Object.keys(metric).filter(x => x.indexOf("_type") < 0 && !(x === 'type'))
        .forEach(key => name = name + "," + key + "=" + metric[key])
    return name
}

export {idForMetric}